package edu.ucsb.cs156.jobs.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.entities.JobLog;
import edu.ucsb.cs156.jobs.repositories.JobLogRepository;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
public class JobContextTests {

  @Mock JobLogRepository jobLogRepository;

  @Mock JobsRepository jobsRepository;

  @Mock PlatformTransactionManager platformTransactionManager;

  @Test
  public void log_inserts_a_JobLog_row_and_does_not_touch_job_getLog() {
    Job job = Job.builder().id(7L).build();
    JobContext context = new JobContext(jobLogRepository, job, null);

    context.log("first line");

    ArgumentCaptor<JobLog> captor = ArgumentCaptor.forClass(JobLog.class);
    verify(jobLogRepository).save(captor.capture());
    assertEquals(7L, captor.getValue().getJobId());
    assertEquals("first line", captor.getValue().getMessage());
    // v0.2.0: log() no longer maintains job.getLog() for a real (non-null-repository) run
    assertNull(job.getLog());
  }

  @Test
  public void each_log_call_is_its_own_insert() {
    Job job = Job.builder().id(7L).build();
    JobContext context = new JobContext(jobLogRepository, job, null);

    context.log("first line");
    context.log("second line");

    verify(jobLogRepository, times(2)).save(any(JobLog.class));
  }

  @Test
  public void getJob_exposes_the_underlying_job() {
    Job job = Job.builder().build();
    JobContext context = new JobContext(jobLogRepository, job, null);

    assertEquals(job, context.getJob());
  }

  @Test
  public void null_jobLogRepository_is_tolerated_as_a_test_seam() {
    Job job = Job.builder().build();
    JobContext context = new JobContext((JobLogRepository) null, job, null);

    context.log("no save, just accumulate");

    assertEquals("no save, just accumulate", job.getLog());
  }

  @Test
  public void deprecated_two_arg_constructor_always_behaves_as_the_null_test_seam() {
    // Even a non-null (mocked) JobsRepository is ignored by the legacy
    // constructor: v0.2.0's log() no longer has any use for JobsRepository,
    // so this constructor exists purely for source compatibility with
    // pre-v0.2.0 test code (`new JobContext(null, job)`), not as an
    // alternate way to get real persistence.
    Job job = Job.builder().build();
    JobContext context = new JobContext(jobsRepository, job);

    context.log("accumulates in-memory regardless");

    assertEquals("accumulates in-memory regardless", job.getLog());
  }

  @Test
  public void subsequent_log_lines_in_the_null_seam_append_with_newline() {
    Job job = Job.builder().log("first line").build();
    JobContext context = new JobContext((JobLogRepository) null, job, null);

    context.log("second line");

    assertEquals("first line\nsecond line", job.getLog());
  }

  @Test
  public void with_a_log_transaction_template_the_insert_runs_inside_it() {
    Job job = Job.builder().id(7L).build();
    TransactionTemplate template = new TransactionTemplate(platformTransactionManager);
    JobContext context = new JobContext(jobLogRepository, job, template);

    context.log("transactional line");

    verify(jobLogRepository).save(any(JobLog.class));
    verify(platformTransactionManager).commit(any());
  }
}
