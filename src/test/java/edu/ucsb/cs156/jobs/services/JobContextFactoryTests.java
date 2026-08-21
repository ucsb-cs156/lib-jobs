package edu.ucsb.cs156.jobs.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.entities.JobLog;
import edu.ucsb.cs156.jobs.errors.JobCancelledException;
import edu.ucsb.cs156.jobs.repositories.JobLogRepository;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import java.util.Optional;
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

  @Mock JobsRepository jobsRepository;

  @Test
  public void createContext_wires_repository_job_and_requires_new_log_transactions() {
    JobContextFactory factory =
        new JobContextFactory(jobLogRepository, transactionManager, jobsRepository);
    Job job = Job.builder().id(1L).status("running").build();
    when(jobsRepository.findById(1L)).thenReturn(Optional.of(job));

    JobContext context = factory.createContext(job);

    assertNotNull(context);
    context.log("hello");
    verify(jobLogRepository).save(any(JobLog.class));

    // one REQUIRES_NEW transaction for the log write, another for the
    // cancellation check -- both must bypass the job body's own long-lived
    // transaction, so both need their own fresh transaction/session
    ArgumentCaptor<TransactionDefinition> definition =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager, times(2)).getTransaction(definition.capture());
    definition
        .getAllValues()
        .forEach(
            d ->
                assertEquals(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW, d.getPropagationBehavior()));
    verify(transactionManager, times(2)).commit(any());
  }

  @Test
  public void createContext_wires_the_JobsRepository_so_cancellation_checking_actually_fires() {
    JobContextFactory factory =
        new JobContextFactory(jobLogRepository, transactionManager, jobsRepository);
    Job job = Job.builder().id(1L).status("cancelling").build();
    when(jobsRepository.findById(1L)).thenReturn(Optional.of(job));

    JobContext context = factory.createContext(job);

    assertThrows(JobCancelledException.class, () -> context.log("hello"));
  }
}
