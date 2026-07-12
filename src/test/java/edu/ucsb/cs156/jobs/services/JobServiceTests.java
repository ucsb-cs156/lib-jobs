package edu.ucsb.cs156.jobs.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.errors.EntityNotFoundException;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
public class JobServiceTests {

  @Mock JobsRepository jobsRepository;

  @Mock JobUserProvider jobUserProvider;

  @Mock JobContextFactory contextFactory;

  @Mock PlatformTransactionManager platformTransactionManager;

  @Mock JobService selfMock;

  JobService jobService;

  @BeforeEach
  public void setup() {
    jobService = new JobService();
    ReflectionTestUtils.setField(jobService, "jobsRepository", jobsRepository);
    ReflectionTestUtils.setField(jobService, "jobUserProvider", jobUserProvider);
    ReflectionTestUtils.setField(jobService, "contextFactory", contextFactory);
    ReflectionTestUtils.setField(
        jobService, "transactionTemplate", new TransactionTemplate(platformTransactionManager));
    ReflectionTestUtils.setField(jobService, "self", selfMock);
  }

  static class DemoScopedJob implements JobContextConsumer {
    @Override
    public void accept(JobContext c) {}

    @Override
    public String getScopeType() {
      return "course";
    }

    @Override
    public Long getScopeId() {
      return 17L;
    }
  }

  @Test
  public void runAsJob_populates_job_and_dispatches_async() {
    when(jobUserProvider.getCurrentUserId()).thenReturn(42L);
    when(jobUserProvider.getCurrentUserEmail()).thenReturn("test@example.org");
    DemoScopedJob jobFunction = new DemoScopedJob();

    Job job = jobService.runAsJob(jobFunction);

    assertEquals(42L, job.getCreatedById());
    assertEquals("test@example.org", job.getCreatedByEmail());
    assertEquals("running", job.getStatus());
    assertEquals("DemoScopedJob", job.getJobName());
    assertEquals("course", job.getScopeType());
    assertEquals(17L, job.getScopeId());
    verify(jobsRepository).save(job);
    verify(selfMock).runJobAsync(job, jobFunction);
  }

  @Test
  public void runAsJob_leaves_scope_null_for_unscoped_jobs() {
    when(jobUserProvider.getCurrentUserId()).thenReturn(42L);
    when(jobUserProvider.getCurrentUserEmail()).thenReturn("test@example.org");
    JobContextConsumer jobFunction = c -> {};

    Job job = jobService.runAsJob(jobFunction);

    assertNull(job.getScopeType());
    assertNull(job.getScopeId());
  }

  @Test
  public void runJobAsync_success_sets_status_complete_and_commits() {
    Job job = Job.builder().status("running").build();
    JobContext context = new JobContext(jobsRepository, job);
    when(contextFactory.createContext(job)).thenReturn(context);
    JobContextConsumer jobFunction = c -> c.log("working");

    jobService.runJobAsync(job, jobFunction);

    assertEquals("complete", job.getStatus());
    assertEquals("working", job.getLog());
    verify(platformTransactionManager).commit(any());
    // one save from context.log, one from the final status update
    verify(jobsRepository, times(2)).save(job);
  }

  @Test
  public void runJobAsync_failure_sets_status_error_logs_and_rolls_back() {
    Job job = Job.builder().status("running").build();
    JobContext context = new JobContext(jobsRepository, job);
    when(contextFactory.createContext(job)).thenReturn(context);
    JobContextConsumer jobFunction =
        c -> {
          throw new Exception("Fail!");
        };

    jobService.runJobAsync(job, jobFunction);

    assertEquals("error", job.getStatus());
    assertEquals("java.lang.Exception: Fail!", job.getLog());
    verify(platformTransactionManager).rollback(any());
    // only the save from context.log; the "complete" save must not happen
    verify(jobsRepository, times(1)).save(job);
  }

  @Test
  public void getJobLogs_returns_log_when_present() {
    Job job = Job.builder().log("line1\nline2").build();
    when(jobsRepository.findById(7L)).thenReturn(Optional.of(job));

    assertEquals("line1\nline2", jobService.getJobLogs(7L));
  }

  @Test
  public void getJobLogs_returns_empty_string_for_null_log() {
    Job job = Job.builder().build();
    when(jobsRepository.findById(7L)).thenReturn(Optional.of(job));

    assertEquals("", jobService.getJobLogs(7L));
  }

  @Test
  public void getJobLogs_throws_EntityNotFoundException_when_missing() {
    when(jobsRepository.findById(7L)).thenReturn(Optional.empty());

    EntityNotFoundException thrown =
        assertThrows(EntityNotFoundException.class, () -> jobService.getJobLogs(7L));
    assertEquals("Job with id 7 not found", thrown.getMessage());
  }
}
