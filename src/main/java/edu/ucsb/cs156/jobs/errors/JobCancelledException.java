package edu.ucsb.cs156.jobs.errors;

/**
 * Unchecked so that {@link edu.ucsb.cs156.jobs.services.JobContext#log} does not need a {@code
 * throws} clause — several already-migrated job bodies call {@code ctx.log(...)} from inside
 * lambdas (e.g. {@code Map.forEach}) whose functional interface doesn't declare checked exceptions,
 * so a checked exception here would break those call sites. Thrown by {@link
 * edu.ucsb.cs156.jobs.services.JobContext#log} when the job's current persisted status is {@code
 * "cancelling"}; caught by {@link edu.ucsb.cs156.jobs.services.JobService#runJobAsync} to set the
 * terminal {@code "cancelled"} status.
 */
public class JobCancelledException extends RuntimeException {
  public JobCancelledException(Long jobId) {
    super("Job %d was cancelled".formatted(jobId));
  }
}
