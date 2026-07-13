package edu.ucsb.cs156.jobs.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
public class JobContextTests {

  @Mock JobsRepository jobsRepository;

  @Mock PlatformTransactionManager platformTransactionManager;

  @Test
  public void first_log_line_replaces_null_log() {
    Job job = Job.builder().build();
    JobContext context = new JobContext(jobsRepository, job);

    context.log("first line");

    assertEquals("first line", job.getLog());
    verify(jobsRepository).save(job);
  }

  @Test
  public void subsequent_log_lines_append_with_newline() {
    Job job = Job.builder().log("first line").build();
    JobContext context = new JobContext(jobsRepository, job);

    context.log("second line");

    assertEquals("first line\nsecond line", job.getLog());
    verify(jobsRepository, times(1)).save(job);
  }

  @Test
  public void null_repository_is_tolerated_as_a_test_seam() {
    Job job = Job.builder().build();
    JobContext context = new JobContext(null, job);

    context.log("no save, just accumulate");

    assertEquals("no save, just accumulate", job.getLog());
  }

  @Test
  public void with_a_log_transaction_template_the_save_runs_inside_it() {
    Job job = Job.builder().build();
    TransactionTemplate template = new TransactionTemplate(platformTransactionManager);
    JobContext context = new JobContext(jobsRepository, job, template);

    context.log("transactional line");

    assertEquals("transactional line", job.getLog());
    verify(jobsRepository).save(job);
    verify(platformTransactionManager).commit(any());
  }
}
