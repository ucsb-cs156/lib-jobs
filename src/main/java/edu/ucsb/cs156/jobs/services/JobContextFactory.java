package edu.ucsb.cs156.jobs.services;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;

/** Creates {@link JobContext} instances; exists as a seam so tests can substitute contexts. */
public class JobContextFactory {
  private final JobsRepository jobsRepository;

  public JobContextFactory(JobsRepository jobsRepository) {
    this.jobsRepository = jobsRepository;
  }

  public JobContext createContext(Job job) {
    return new JobContext(jobsRepository, job);
  }
}
