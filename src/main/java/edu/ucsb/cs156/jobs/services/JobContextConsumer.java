package edu.ucsb.cs156.jobs.services;

/**
 * A concrete job: the code that runs asynchronously, receiving a {@link JobContext} for logging.
 * Apps implement this interface (or pass a lambda) and hand instances to {@link
 * JobService#runAsJob}.
 */
@FunctionalInterface
public interface JobContextConsumer {
  void accept(JobContext c) throws Exception;

  /** Name recorded on the {@code Job} row and shown in the admin UI. */
  default String getJobName() {
    return getClass().getSimpleName();
  }

  /**
   * Optional scope: the kind of app-domain object this job belongs to (e.g. {@code "course"}). Null
   * (the default) means the job is unscoped.
   */
  default String getScopeType() {
    return null;
  }

  /** Optional scope: the id of the app-domain object this job belongs to. */
  default Long getScopeId() {
    return null;
  }
}
