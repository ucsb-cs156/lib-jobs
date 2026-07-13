package edu.ucsb.cs156.jobs.services;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates {@link JobContext} instances; exists as a seam so tests can substitute contexts.
 *
 * <p>Contexts are given a REQUIRES_NEW transaction template for log writes, so each log line
 * commits (and becomes visible to the admin UI) immediately, independent of the job body's
 * all-or-nothing transaction.
 */
public class JobContextFactory {
  private final JobsRepository jobsRepository;
  private final TransactionTemplate logTransactionTemplate;

  public JobContextFactory(
      JobsRepository jobsRepository, PlatformTransactionManager transactionManager) {
    this.jobsRepository = jobsRepository;
    this.logTransactionTemplate = new TransactionTemplate(transactionManager);
    this.logTransactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public JobContext createContext(Job job) {
    return new JobContext(jobsRepository, job, logTransactionTemplate);
  }
}
