package edu.ucsb.cs156.jobs;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.entities.JobLog;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import edu.ucsb.cs156.jobs.services.JobContextConsumer;
import edu.ucsb.cs156.jobs.services.JobService;
import edu.ucsb.cs156.jobs.testapp.TestApplication;
import edu.ucsb.cs156.jobs.testapp.TestJob;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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

  /**
   * The live-log guarantee: each log line commits in its own transaction, so it is visible to other
   * connections (i.e. the admin UI) while the job is still running. Before v0.1.4, log writes
   * joined the job body's wrapping transaction and nothing was visible until the job finished.
   */
  @Test
  public void log_lines_are_visible_to_other_connections_while_the_job_is_still_running()
      throws Exception {
    CountDownLatch canFinish = new CountDownLatch(1);

    JobContextConsumer blockedJob =
        c -> {
          c.log("progress line 1");
          canFinish.await();
          c.log("progress line 2");
        };

    Job launched = jobService.runAsJob(blockedJob);
    Job queuedBehind = null;
    try {
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> "progress line 1".equals(jobService.getJobLogs(launched.getId())));
      // the job must still be mid-run when its first log line became visible
      assertEquals("running", jobsRepository.findById(launched.getId()).orElseThrow().getStatus());

      // a job launched while the single-threaded executor is busy reports
      // "queued" (not "running") until the executor picks it up
      queuedBehind = jobService.runAsJob(TestJob.builder().build());
      assertEquals(
          "queued", jobsRepository.findById(queuedBehind.getId()).orElseThrow().getStatus());
    } finally {
      canFinish.countDown();
    }

    Job finished = awaitFinished(launched.getId());
    assertEquals("complete", finished.getStatus());
    assertEquals("progress line 1\nprogress line 2", jobService.getJobLogs(launched.getId()));
    assertEquals("complete", awaitFinished(queuedBehind.getId()).getStatus());
  }

  @Test
  public void successful_job_completes_with_live_log_and_creator_stamped() {
    TestJob testJob = TestJob.builder().count(2).sleepMs(1).build();

    Job launched = jobService.runAsJob(testJob);

    Job finished = awaitFinished(launched.getId());
    assertEquals("complete", finished.getStatus());
    assertEquals(
        "Hello World! i=0\nHello World! i=1\nGoodbye from TestJob!",
        jobService.getJobLogs(finished.getId()));
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
    String log = jobService.getJobLogs(finished.getId());
    assertTrue(log.contains("Fail!"), "log was: " + log);
  }

  @Test
  public void getJobLogTail_returns_only_lines_logged_after_the_given_cursor() {
    TestJob testJob = TestJob.builder().count(2).sleepMs(1).build();

    Job launched = jobService.runAsJob(testJob);
    Job finished = awaitFinished(launched.getId());

    List<JobLog> all = jobService.getJobLogTail(finished.getId(), 0L);
    assertEquals(3, all.size());
    assertEquals("Hello World! i=0", all.get(0).getMessage());

    List<JobLog> afterFirst = jobService.getJobLogTail(finished.getId(), all.get(0).getId());
    assertEquals(2, afterFirst.size());
    assertEquals("Hello World! i=1", afterFirst.get(0).getMessage());
    assertEquals("Goodbye from TestJob!", afterFirst.get(1).getMessage());

    List<JobLog> afterLast = jobService.getJobLogTail(finished.getId(), all.get(2).getId());
    assertEquals(0, afterLast.size());
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

    // scoped's job_logs rows must not block the delete: FK_JOB_LOGS_JOBS is
    // ON DELETE CASCADE precisely so a job with logged output can still be deleted
    jobsRepository.deleteByScopeTypeAndScopeId("course", 17L);
    assertEquals(1, jobsRepository.count());
    assertEquals(unscoped.getId(), jobsRepository.findAll().get(0).getId());
  }

  /**
   * The running-job cancellation path: a cancel request flips the row to "cancelling" while the job
   * is mid-run; the job body's next {@code ctx.log()} checkpoint (no code changes needed in the job
   * body itself) throws, and {@code JobService} lands the job in the terminal "cancelled" status
   * without ever reaching the log line after the checkpoint.
   */
  @Test
  public void a_running_job_stops_at_its_next_checkpoint_when_cancelled() throws Exception {
    CountDownLatch canFinish = new CountDownLatch(1);

    JobContextConsumer blockedJob =
        c -> {
          c.log("checkpoint 1");
          canFinish.await();
          // the checkpoint that detects cancellation still logs its own message
          // first (design: "log the message first, then check") -- only the
          // checkpoint AFTER that one must never run
          c.log("checkpoint 2");
          c.log("checkpoint 3"); // must never appear
        };

    Job launched = jobService.runAsJob(blockedJob);
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> "checkpoint 1".equals(jobService.getJobLogs(launched.getId())));

    // simulate POST /api/jobs/{id}/cancel on a running job
    Job toCancel = jobsRepository.findById(launched.getId()).orElseThrow();
    toCancel.setStatus("cancelling");
    jobsRepository.save(toCancel);

    canFinish.countDown();

    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                "cancelled"
                    .equals(jobsRepository.findById(launched.getId()).orElseThrow().getStatus()));
    assertEquals("checkpoint 1\ncheckpoint 2", jobService.getJobLogs(launched.getId()));
  }

  /**
   * The queued-job cancellation path: a cancel request on a job that's still waiting behind another
   * one in the single-threaded executor's FIFO queue kills it directly and instantly (nothing is
   * executing yet) -- the executor must skip invoking its body entirely once it's finally free to
   * pick the job up.
   */
  @Test
  public void a_queued_job_never_runs_when_cancelled_before_the_executor_picks_it_up()
      throws Exception {
    CountDownLatch canFinish = new CountDownLatch(1);
    JobContextConsumer blockingJob = c -> canFinish.await();

    Job busy = jobService.runAsJob(blockingJob);
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                "running".equals(jobsRepository.findById(busy.getId()).orElseThrow().getStatus()));

    Job queued = jobService.runAsJob(TestJob.builder().build());
    assertEquals("queued", jobsRepository.findById(queued.getId()).orElseThrow().getStatus());

    // simulate POST /api/jobs/{id}/cancel on the still-queued job
    Job toCancel = jobsRepository.findById(queued.getId()).orElseThrow();
    toCancel.setStatus("cancelled");
    jobsRepository.save(toCancel);

    canFinish.countDown();
    assertEquals("complete", awaitFinished(busy.getId()).getStatus());

    // give the now-free executor a moment to pick up (and skip) the cancelled job
    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                "cancelled"
                    .equals(jobsRepository.findById(queued.getId()).orElseThrow().getStatus()));
    assertEquals("", jobService.getJobLogs(queued.getId()));
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

  /**
   * Proves the recovery sweep's query and update actually work against a real database, not just a
   * mocked repository -- seeds rows directly (bypassing the executor entirely, standing in for jobs
   * left behind by a *previous* run that crashed or was killed) rather than relying on the
   * ApplicationReadyEvent firing again mid-test, since the Spring context (and its one startup
   * event) is already up before this test method runs.
   */
  @Test
  public void recoverInterruptedJobsOnStartup_marks_orphaned_jobs_interrupted_for_real() {
    Job orphanedQueued = jobsRepository.save(Job.builder().status("queued").build());
    Job orphanedRunning = jobsRepository.save(Job.builder().status("running").build());
    Job orphanedCancelling = jobsRepository.save(Job.builder().status("cancelling").build());
    Job stillComplete = jobsRepository.save(Job.builder().status("complete").build());

    jobService.recoverInterruptedJobsOnStartup();

    assertEquals(
        "interrupted", jobsRepository.findById(orphanedQueued.getId()).orElseThrow().getStatus());
    assertEquals(
        "interrupted", jobsRepository.findById(orphanedRunning.getId()).orElseThrow().getStatus());
    assertEquals(
        "interrupted",
        jobsRepository.findById(orphanedCancelling.getId()).orElseThrow().getStatus());
    assertEquals(
        "complete", jobsRepository.findById(stillComplete.getId()).orElseThrow().getStatus());
  }
}
