package edu.ucsb.cs156.jobs.services;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handed to a running {@link JobContextConsumer}; each {@link #log} call appends a line to the
 * job's persistent log and saves it, so admins can watch progress live.
 *
 * <p>The job body runs inside one all-or-nothing transaction (see {@link JobService#runJobAsync}),
 * so log saves must NOT join it: they would be invisible to the admin UI until the whole job
 * commits. When a {@code logTransactionTemplate} is provided (configured REQUIRES_NEW by {@link
 * JobContextFactory}), each log line commits in its own transaction immediately.
 *
 * <p>A null repository is tolerated (the log accumulates on the entity without being persisted):
 * the apps' job tests conventionally run jobs against {@code new JobContext(null, job)} and assert
 * on {@code job.getLog()}.
 */
@Slf4j
public class JobContext {
  private final JobsRepository jobsRepository;
  private final Job job;
  private final TransactionTemplate logTransactionTemplate;

  public JobContext(JobsRepository jobsRepository, Job job) {
    this(jobsRepository, job, null);
  }

  public JobContext(
      JobsRepository jobsRepository, Job job, TransactionTemplate logTransactionTemplate) {
    this.jobsRepository = jobsRepository;
    this.job = job;
    this.logTransactionTemplate = logTransactionTemplate;
  }

  public void log(String message) {
    log.info("Job {}: {}", job.getId(), message);
    String previousLog = job.getLog() == null ? "" : (job.getLog() + "\n");
    job.setLog(previousLog + message);
    if (jobsRepository == null) {
      return;
    }
    if (logTransactionTemplate != null) {
      logTransactionTemplate.executeWithoutResult(status -> jobsRepository.save(job));
    } else {
      jobsRepository.save(job);
    }
  }

  /**
   * Exposes the underlying job, mainly so job bodies and their tests can inspect state (e.g. {@code
   * ctx.getJob().getLog()}) without threading a separate reference through.
   */
  public Job getJob() {
    return job;
  }
}
