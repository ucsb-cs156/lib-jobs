package edu.ucsb.cs156.jobs;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import edu.ucsb.cs156.jobs.services.JobService;
import edu.ucsb.cs156.jobs.testapp.TestApplication;
import edu.ucsb.cs156.jobs.testapp.TestJob;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * End-to-end proof that the auto-configured stack works against a real database: launch through
 * JobService, run on the jobsExecutor thread, watch the persisted status and log.
 */
@SpringBootTest(classes = TestApplication.class)
public class JobsIntegrationTests {

  @Autowired JobService jobService;

  @Autowired JobsRepository jobsRepository;

  @BeforeEach
  public void cleanSlate() {
    jobsRepository.deleteAll();
  }

  private Job awaitFinished(long jobId) {
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () -> {
              String status = jobsRepository.findById(jobId).orElseThrow().getStatus();
              return status.equals("complete") || status.equals("error");
            });
    return jobsRepository.findById(jobId).orElseThrow();
  }

  @Test
  public void successful_job_completes_with_live_log_and_creator_stamped() {
    TestJob testJob = TestJob.builder().count(2).sleepMs(1).build();

    Job launched = jobService.runAsJob(testJob);

    Job finished = awaitFinished(launched.getId());
    assertEquals("complete", finished.getStatus());
    assertEquals("Hello World! i=0\nHello World! i=1\nGoodbye from TestJob!", finished.getLog());
    assertEquals("TestJob", finished.getJobName());
    assertEquals(42L, finished.getCreatedById());
    assertEquals("test@example.org", finished.getCreatedByEmail());
    assertNotNull(finished.getCreatedAt());
    assertNotNull(finished.getUpdatedAt());
  }

  @Test
  public void failing_job_ends_in_error_status_with_exception_in_log() {
    TestJob testJob = TestJob.builder().fail(true).build();

    Job launched = jobService.runAsJob(testJob);

    Job finished = awaitFinished(launched.getId());
    assertEquals("error", finished.getStatus());
    assertTrue(finished.getLog().contains("Fail!"), "log was: " + finished.getLog());
  }

  @Test
  public void scoped_jobs_can_be_listed_and_deleted_by_scope() {
    Job scoped = jobService.runAsJob(TestJob.builder().scopeType("course").scopeId(17L).build());
    Job unscoped = jobService.runAsJob(TestJob.builder().build());
    awaitFinished(scoped.getId());
    awaitFinished(unscoped.getId());

    List<Job> forCourse17 = new ArrayList<>();
    jobsRepository.findByScopeTypeAndScopeIdOrderByIdDesc("course", 17L).forEach(forCourse17::add);
    assertEquals(1, forCourse17.size());
    assertEquals(scoped.getId(), forCourse17.get(0).getId());

    jobsRepository.deleteByScopeTypeAndScopeId("course", 17L);
    assertEquals(1, jobsRepository.count());
    assertEquals(unscoped.getId(), jobsRepository.findAll().get(0).getId());
  }

  @Test
  public void findAllByOrderByIdDesc_returns_newest_first() {
    Job first = jobService.runAsJob(TestJob.builder().build());
    Job second = jobService.runAsJob(TestJob.builder().build());
    awaitFinished(first.getId());
    awaitFinished(second.getId());

    List<Job> jobs = new ArrayList<>();
    jobsRepository.findAllByOrderByIdDesc().forEach(jobs::add);
    assertEquals(2, jobs.size());
    assertEquals(second.getId(), jobs.get(0).getId());
    assertEquals(first.getId(), jobs.get(1).getId());
  }
}
