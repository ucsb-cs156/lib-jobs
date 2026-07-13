package edu.ucsb.cs156.jobs.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.entities.JobLog;
import edu.ucsb.cs156.jobs.repositories.JobLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

@ExtendWith(MockitoExtension.class)
public class JobContextFactoryTests {

  @Mock JobLogRepository jobLogRepository;

  @Mock PlatformTransactionManager transactionManager;

  @Test
  public void createContext_wires_repository_job_and_requires_new_log_transactions() {
    JobContextFactory factory = new JobContextFactory(jobLogRepository, transactionManager);
    Job job = Job.builder().id(1L).build();

    JobContext context = factory.createContext(job);

    assertNotNull(context);
    context.log("hello");
    verify(jobLogRepository).save(any(JobLog.class));

    ArgumentCaptor<TransactionDefinition> definition =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager).getTransaction(definition.capture());
    assertEquals(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW,
        definition.getValue().getPropagationBehavior());
    verify(transactionManager).commit(any());
  }
}
