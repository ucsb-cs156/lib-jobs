package edu.ucsb.cs156.jobs.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.entities.JobLog;
import edu.ucsb.cs156.jobs.errors.EntityNotFoundException;
import edu.ucsb.cs156.jobs.repositories.JobLogRepository;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import java.util.ArrayList;
import java.util.List;
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
