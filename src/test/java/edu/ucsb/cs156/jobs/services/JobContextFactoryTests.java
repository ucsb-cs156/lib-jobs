package edu.ucsb.cs156.jobs.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import edu.ucsb.cs156.jobs.entities.Job;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

@ExtendWith(MockitoExtension.class)
public class JobContextFactoryTests {

  @Mock edu.ucsb.cs156.jobs.repositories.JobsRepository jobsRepository;

  @Mock PlatformTransactionManager transactionManager;

  @Test
  public void createContext_wires_repository_job_and_requires_new_log_transactions() {
    JobContextFactory factory = new JobContextFactory(jobsRepository, transactionManager);
    Job job = Job.builder().build();

    JobContext context = factory.createContext(job);

    assertNotNull(context);
    context.log("hello");
    assertEquals("hello", job.getLog());
    verify(jobsRepository).save(job);

    // each log line must commit in its own transaction, independent of the
    // job body's wrapping transaction, so it is visible to the admin UI live
    ArgumentCaptor<TransactionDefinition> definition =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager).getTransaction(definition.capture());
    assertEquals(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW,
        definition.getValue().getPropagationBehavior());
    verify(transactionManager).commit(any());
  }
}
