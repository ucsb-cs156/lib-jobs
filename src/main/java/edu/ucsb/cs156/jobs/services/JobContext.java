package edu.ucsb.cs156.jobs.services;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.entities.JobLog;
import edu.ucsb.cs156.jobs.repositories.JobLogRepository;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handed to a running {@link JobContextConsumer}; each {@link #log} call appends one line to the
 * job's log, so admins can watch progress live.
 *
 * <p>Since v0.2.0, each line is its own row in {@code job_logs} (see {@link JobLog}) rather than a
 * read-modify-write of one growing TEXT column on {@code jobs} — an O(1) insert instead of an O(N)
 * rewrite of everything logged so far. The job body runs inside one all-or-nothing transaction (see
 * {@link JobService#runJobAsync}), so log writes must NOT join it: they would be invisible to the
 * admin UI until the whole job commits. When a {@code logTransactionTemplate} is provided
 * (configured REQUIRES_NEW by {@link JobContextFactory}), each line commits in its own transaction
 * immediately.
 *
 * <p>A null {@code jobLogRepository} is tolerated as a test seam: the log accumulates on {@code
 * job}'s (Java-only, {@code @Transient}) {@code log} field instead of being persisted anywhere. The
 * apps' job tests conventionally run jobs against {@code new JobContext(null, job)} and assert on
 * {@code job.getLog()}; that legacy two-arg constructor is preserved unchanged for exactly this
 * reason.
 */
@Slf4j
public class JobContext {
  private final JobLogRepository jobLogRepository;
  private final Job job;
  private final TransactionTemplate logTransactionTemplate;

  /**
   * @deprecated kept only so existing test code written against v0.1.x ({@code new JobContext(null,
   *     job)}) keeps compiling; the {@code jobsRepository} parameter is ignored. Use {@link
   *     #JobContext(JobLogRepository, Job, TransactionTemplate)}.
   */
  @Deprecated
  public JobContext(JobsRepository jobsRepository, Job job) {
    this((JobLogRepository) null, job, null);
  }

  public JobContext(
      JobLogRepository jobLogRepository, Job job, TransactionTemplate logTransactionTemplate) {
    this.jobLogRepository = jobLogRepository;
    this.job = job;
    this.logTransactionTemplate = logTransactionTemplate;
  }

  public void log(String message) {
    log.info("Job {}: {}", job.getId(), message);
    if (jobLogRepository == null) {
      // Test seam: no persistence available, so accumulate in-memory on the
      // (Java-only) Job.log field, exactly as v0.1.x did for every caller.
      String previousLog = job.getLog() == null ? "" : (job.getLog() + "\n");
      job.setLog(previousLog + message);
      return;
    }
    JobLog entry = JobLog.builder().jobId(job.getId()).message(message).build();
    if (logTransactionTemplate != null) {
      logTransactionTemplate.executeWithoutResult(status -> jobLogRepository.save(entry));
    } else {
      jobLogRepository.save(entry);
    }
  }

  /**
   * Exposes the underlying job, mainly so job bodies and their tests can inspect state (e.g. {@code
   * ctx.getJob().getLog()}) without threading a separate reference through. Note: for a real
   * (non-test-seam) run, {@code job.getLog()} is not kept up to date by {@link #log} any more (see
   * class javadoc) — it reflects whatever the caller last set it to, typically nothing.
   */
  public Job getJob() {
    return job;
  }
}
