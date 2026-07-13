package edu.ucsb.cs156.jobs.services;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handed to a running {@link JobContextConsumer}; each {@link #log} call appends a line to the
 * job's persistent log and saves it, so admins can watch progress live.
 *
 * <p>A null repository is tolerated (the log accumulates on the entity without being persisted):
 * the apps' job tests conventionally run jobs against {@code new JobContext(null, job)} and assert
 * on {@code job.getLog()}.
 */
@AllArgsConstructor
@Slf4j
public class JobContext {
  private JobsRepository jobsRepository;
  private Job job;

  public void log(String message) {
    log.info("Job {}: {}", job.getId(), message);
    String previousLog = job.getLog() == null ? "" : (job.getLog() + "\n");
    job.setLog(previousLog + message);
    if (jobsRepository != null) {
      jobsRepository.save(job);
    }
  }
}
