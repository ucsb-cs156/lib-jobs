package edu.ucsb.cs156.jobs.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.entities.JobLog;
import edu.ucsb.cs156.jobs.errors.EntityNotFoundException;
import edu.ucsb.cs156.jobs.errors.JobCancelledException;
import edu.ucsb.cs156.jobs.repositories.JobLogRepository;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
public class JobServiceTests {

  @Mock JobsRepository jobsRepository;

  @Mock JobLogRepository jobLogRepository;

  @Mock JobUserProvider jobUserProvider;

  @Mock JobContextFactory contextFactory;

  @Mock PlatformTransactionManager platformTransactionManager;

  @Mock JobService selfMock;

  JobService jobService;

  @BeforeEach
  public void setup() {
    jobService = new JobService();
    ReflectionTestUtils.setField(jobService, "jobsRepository", jobsRepository);
    ReflectionTestUtils.setField(jobService, "jobLogRepository", jobLogRepository);
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
    assertEquals("queued", job.getStatus());
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
    Job job = Job.builder().id(1L).status("queued").build();
    JobContext context = new JobContext(jobLogRepository, job, null);
    when(contextFactory.createContext(job)).thenReturn(context);
    List<String> observedStatuses = new ArrayList<>();
    JobContextConsumer jobFunction =
        c -> {
          observedStatuses.add(job.getStatus());
          c.log("working");
        };

    jobService.runJobAsync(job, jobFunction);

    // the status must move to "running" before the job body executes
    assertEquals(List.of("running"), observedStatuses);
    assertEquals("complete", job.getStatus());
    verify(platformTransactionManager).commit(any());
    // one save for "running", one for the final "complete" status update
    verify(jobsRepository, times(2)).save(job);

    ArgumentCaptor<JobLog> logCaptor = ArgumentCaptor.forClass(JobLog.class);
    verify(jobLogRepository).save(logCaptor.capture());
    assertEquals("working", logCaptor.getValue().getMessage());
  }

  @Test
  public void runJobAsync_failure_sets_status_error_logs_and_rolls_back() {
    Job job = Job.builder().id(1L).status("queued").build();
    JobContext context = new JobContext(jobLogRepository, job, null);
    when(contextFactory.createContext(job)).thenReturn(context);
    JobContextConsumer jobFunction =
        c -> {
          throw new Exception("Fail!");
        };

    jobService.runJobAsync(job, jobFunction);

    assertEquals("error", job.getStatus());
    verify(platformTransactionManager).rollback(any());
    // one save for "running", one for the "error" status update
    verify(jobsRepository, times(2)).save(job);

    ArgumentCaptor<JobLog> logCaptor = ArgumentCaptor.forClass(JobLog.class);
    verify(jobLogRepository).save(logCaptor.capture());
    assertEquals("java.lang.Exception: Fail!", logCaptor.getValue().getMessage());
  }

  @Test
  public void runJobAsync_skips_execution_when_already_cancelled_while_queued() {
    // the cancel endpoint kills a still-queued job directly (nothing is
    // executing yet), so by the time the executor picks it up the
    // *persisted* row already reads "cancelled" -- even though the
    // in-memory `job` passed in here is stale and still says "queued"
    Job job = Job.builder().id(1L).status("queued").build();
    Job current = Job.builder().id(1L).status("cancelled").build();
    when(jobsRepository.findById(1L)).thenReturn(Optional.of(current));
    AtomicBoolean invoked = new AtomicBoolean(false);
    JobContextConsumer jobFunction = c -> invoked.set(true);

    jobService.runJobAsync(job, jobFunction);

    assertFalse(invoked.get());
    verify(contextFactory, never()).createContext(any());
    verify(jobsRepository, never()).save(any());
  }

  @Test
  public void runJobAsync_sets_status_cancelled_when_job_body_throws_JobCancelledException() {
    Job job = Job.builder().id(1L).status("queued").build();
    when(jobsRepository.findById(1L)).thenReturn(Optional.of(job));
    JobContext context = new JobContext(jobLogRepository, job, null);
    when(contextFactory.createContext(job)).thenReturn(context);
    JobContextConsumer jobFunction =
        c -> {
          throw new JobCancelledException(1L);
        };

    jobService.runJobAsync(job, jobFunction);

    assertEquals("cancelled", job.getStatus());
    verify(platformTransactionManager).rollback(any());
    // one save for "running", one for the "cancelled" status update
    verify(jobsRepository, times(2)).save(job);
  }

  @Test
  public void recoverInterruptedJobsOnStartup_marks_orphaned_jobs_interrupted() {
    Job wasQueued = Job.builder().id(1L).status("queued").build();
    Job wasRunning = Job.builder().id(2L).status("running").build();
    Job wasCancelling = Job.builder().id(3L).status("cancelling").build();
    when(jobsRepository.findByStatusIn(List.of("queued", "running", "cancelling")))
        .thenReturn(List.of(wasQueued, wasRunning, wasCancelling));

    jobService.recoverInterruptedJobsOnStartup();

    assertEquals("interrupted", wasQueued.getStatus());
    assertEquals("interrupted", wasRunning.getStatus());
    assertEquals("interrupted", wasCancelling.getStatus());
    verify(jobsRepository).save(wasQueued);
    verify(jobsRepository).save(wasRunning);
    verify(jobsRepository).save(wasCancelling);
  }

  @Test
  public void recoverInterruptedJobsOnStartup_does_nothing_when_no_orphaned_jobs() {
    when(jobsRepository.findByStatusIn(List.of("queued", "running", "cancelling")))
        .thenReturn(List.of());

    jobService.recoverInterruptedJobsOnStartup();

    verify(jobsRepository, never()).save(any());
  }

  @Test
  public void getJobLogs_joins_log_lines_in_order() {
    when(jobsRepository.existsById(7L)).thenReturn(true);
    when(jobLogRepository.findByJobIdOrderByIdAsc(7L))
        .thenReturn(
            List.of(
                JobLog.builder().jobId(7L).message("line1").build(),
                JobLog.builder().jobId(7L).message("line2").build()));

    assertEquals("line1\nline2", jobService.getJobLogs(7L));
  }

  @Test
  public void getJobLogs_returns_empty_string_when_no_lines_logged() {
    when(jobsRepository.existsById(7L)).thenReturn(true);
    when(jobLogRepository.findByJobIdOrderByIdAsc(7L)).thenReturn(List.of());

    assertEquals("", jobService.getJobLogs(7L));
  }

  @Test
  public void getJobLogs_throws_EntityNotFoundException_when_missing() {
    when(jobsRepository.existsById(7L)).thenReturn(false);

    EntityNotFoundException thrown =
        assertThrows(EntityNotFoundException.class, () -> jobService.getJobLogs(7L));
    assertEquals("Job with id 7 not found", thrown.getMessage());
  }

  @Test
  public void getJobLogTail_returns_lines_after_the_given_id() {
    when(jobsRepository.existsById(7L)).thenReturn(true);
    List<JobLog> newLines = List.of(JobLog.builder().jobId(7L).message("line3").build());
    when(jobLogRepository.findByJobIdAndIdGreaterThanOrderByIdAsc(7L, 2L)).thenReturn(newLines);

    assertEquals(newLines, jobService.getJobLogTail(7L, 2L));
  }

  @Test
  public void getJobLogTail_throws_EntityNotFoundException_when_missing() {
    when(jobsRepository.existsById(7L)).thenReturn(false);

    assertThrows(EntityNotFoundException.class, () -> jobService.getJobLogTail(7L, 0L));
  }

  @Test
  public void getJobLogPreview_reverses_the_newest_first_query_to_chronological_order() {
    when(jobLogRepository.findTop10ByJobIdOrderByIdDesc(7L))
        .thenReturn(
            List.of(
                JobLog.builder().jobId(7L).message("newest").build(),
                JobLog.builder().jobId(7L).message("oldest of the tail").build()));

    assertEquals("oldest of the tail\nnewest", jobService.getJobLogPreview(7L));
  }
}
