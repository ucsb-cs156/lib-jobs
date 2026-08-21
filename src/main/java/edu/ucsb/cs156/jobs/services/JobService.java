package edu.ucsb.cs156.jobs.services;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.entities.JobLog;
import edu.ucsb.cs156.jobs.errors.EntityNotFoundException;
import edu.ucsb.cs156.jobs.errors.JobCancelledException;
import edu.ucsb.cs156.jobs.repositories.JobLogRepository;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates {@link Job} rows and runs {@link JobContextConsumer}s asynchronously on the {@code
 * jobsExecutor}, recording status and log output as they run.
 */
@Slf4j
public class JobService {
  @Autowired private JobsRepository jobsRepository;

  @Autowired private JobLogRepository jobLogRepository;

  @Autowired private JobUserProvider jobUserProvider;

  @Autowired private JobContextFactory contextFactory;

  /*
   * This is a self-referential bean so that runJobAsync is invoked through the
   * Spring proxy; a plain this.runJobAsync(...) call would bypass @Async.
   */
  @Lazy @Autowired private JobService self;

  @Autowired private TransactionTemplate transactionTemplate;

  public Job runAsJob(JobContextConsumer jobFunction) {
    Job job =
        Job.builder()
            .createdById(jobUserProvider.getCurrentUserId())
            .createdByEmail(jobUserProvider.getCurrentUserEmail())
            .status("queued")
            .jobName(jobFunction.getJobName())
            .scopeType(jobFunction.getScopeType())
            .scopeId(jobFunction.getScopeId())
            .build();

    jobsRepository.save(job);
    log.info("Queued job: {}, jobName={}", job.getId(), job.getJobName());
    self.runJobAsync(job, jobFunction);

    return job;
  }

  /**
   * Runs a job asynchronously.
   *
   * <p>This method uses a TransactionTemplate because outside of the Spring context, you cannot
   * delete entities that are unmanaged by Hibernate. Using the transactionTemplate lambda keeps the
   * database session open and allows Hibernate to maintain its knowledge of the object graph (i.e.
   * the entities).
   *
   * <p>Note that using the transactionTemplate lambda means that if there is an unhandled
   * exception, either every database transaction succeeds, or all of them are rolled back.
   *
   * <p>However, the job entity metadata will still be saved.
   *
   * @param job metadata entity about the job
   * @param jobFunction runnable job function
   */
  @Async("jobsExecutor")
  public void runJobAsync(Job job, JobContextConsumer jobFunction) {
    /*
     * A cancel request may have arrived while this job was still queued
     * behind another one; that's the one case that's fully, instantly
     * killable (nothing is executing yet), so the cancel endpoint sets
     * "cancelled" directly rather than "cancelling". Honor that here by
     * re-fetching (the in-memory `job` is stale relative to that write)
     * and skipping the job body entirely if so.
     */
    Job current = jobsRepository.findById(job.getId()).orElse(job);
    if ("cancelled".equals(current.getStatus())) {
      return;
    }

    /*
     * The job may have waited in the executor queue (it runs one job at a
     * time by default); "running" is only truthful once we get here. This
     * save is outside the wrapping transaction, so it is visible immediately.
     */
    job.setStatus("running");
    jobsRepository.save(job);

    JobContext context = contextFactory.createContext(job);

    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            try {
              jobFunction.accept(context);
              /*lambdas cannot throw checked exceptions
              have to repackage as a runtime exception
              to catch outside transactional boundary*/
            } catch (JobCancelledException e) {
              // already unchecked; propagate as itself so the outer catch
              // below can tell a cancellation apart from a real error
              throw e;
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
    } catch (JobCancelledException e) {
      job.setStatus("cancelled");
      jobsRepository.save(job);
      return;
    } catch (Exception e) {
      job.setStatus("error");
      jobsRepository.save(job);
      context.log(e.getMessage());
      return;
    }

    job.setStatus("complete");
    jobsRepository.save(job);
  }

  /**
   * Any job still {@code queued}, {@code running}, or {@code cancelling} at startup is guaranteed
   * orphaned: {@code jobsExecutor}'s in-memory queue does not survive a restart, so nothing is
   * actually executing (or about to execute, or checking for a cancellation request on) that job
   * anymore, and it would otherwise stay stuck in that non-terminal status forever (see DESIGN.md
   * §9's "Also deferred, related" note). Marks each as the terminal {@code interrupted} status so
   * admins see an honest state instead of a phantom "running" (or "cancelling") job that will never
   * progress -- and, critically, so a stuck job from a *previous* run can never be mistaken for one
   * still occupying the (single-threaded, by default) executor after this restart.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void recoverInterruptedJobsOnStartup() {
    Iterable<Job> orphaned =
        jobsRepository.findByStatusIn(List.of("queued", "running", "cancelling"));
    for (Job job : orphaned) {
      job.setStatus("interrupted");
      jobsRepository.save(job);
    }
  }

  /** The full log for one job, oldest line first. */
  public String getJobLogs(Long jobId) {
    if (!jobsRepository.existsById(jobId)) {
      throw new EntityNotFoundException(Job.class, jobId);
    }
    List<JobLog> entries = jobLogRepository.findByJobIdOrderByIdAsc(jobId);
    return entries.stream().map(JobLog::getMessage).collect(Collectors.joining("\n"));
  }

  /**
   * Everything logged for this job since {@code afterId} (exclusive), oldest first — the
   * incremental "tail -f" query: a polling client passes the highest id it has already seen and
   * gets back only new lines.
   */
  public List<JobLog> getJobLogTail(Long jobId, Long afterId) {
    if (!jobsRepository.existsById(jobId)) {
      throw new EntityNotFoundException(Job.class, jobId);
    }
    return jobLogRepository.findByJobIdAndIdGreaterThanOrderByIdAsc(jobId, afterId);
  }

  /**
   * The most recent log lines for one job, joined into a single string oldest-first — used to
   * populate a preview on list/paginated responses without shipping each job's entire log. Assumes
   * the job id is already known-valid (the caller has just fetched a page of {@link Job} rows).
   */
  public String getJobLogPreview(Long jobId) {
    List<JobLog> tail = jobLogRepository.findTop10ByJobIdOrderByIdDesc(jobId);
    List<JobLog> chronological = new ArrayList<>(tail);
    Collections.reverse(chronological);
    return chronological.stream().map(JobLog::getMessage).collect(Collectors.joining("\n"));
  }
}
