package edu.ucsb.cs156.jobs;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import edu.ucsb.cs156.jobs.services.JobContextConsumer;
import edu.ucsb.cs156.jobs.services.JobService;
import edu.ucsb.cs156.jobs.testapp.TestApplication;
import edu.ucsb.cs156.jobs.testapp.TestJob;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves job chaining -- a job's own {@code accept()} calling {@code jobService.runAsJob(child)} as
 * its last statement -- is safe with more than one {@code jobsExecutor} thread. See
 * docs/job-chaining-analysis.md for the full transaction-visibility mechanics this guards against:
 * without {@code runAsJob}'s REQUIRES_NEW save, the child's initial "queued" row stays uncommitted
 * (joined to the parent's own still-open job-body transaction) while a second executor thread could
 * already be racing to update it, permanently stranding the child at "queued" even though its
 * actual work completes normally.
 *
 * <p>{@code core-pool-size}/{@code max-pool-size} are both set to 2 here specifically (the default
 * everywhere else in this test suite is 1, which is single-threaded FIFO and therefore incapable of
 * reproducing this race at all -- see {@link JobsIntegrationTests}) so the child gets its own
 * worker thread genuinely concurrent with the still-running parent.
 */
@SpringBootTest(
    classes = TestApplication.class,
    properties = {"app.jobs.core-pool-size=2", "app.jobs.max-pool-size=2"})
public class JobChainingIntegrationTests {

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
   * The parent deliberately stays inside its own job-body transaction (blocked on a latch) for a
   * moment after launching the child, giving the child's own worker thread a chance to attempt
   * (and, without the fix, silently no-op) its own "running"/"complete" status updates while the
   * child's initial INSERT is still joined to the parent's not-yet-committed transaction. Only once
   * the parent's latch is released -- and its transaction actually commits, making the child's row
   * visible to other connections for the first time -- do we check the child's final status:
   * without the fix, every update the child's own thread could ever make already ran (and silently
   * no-op'd) before this point, so it can only ever read back as permanently stuck on "queued", no
   * matter how long we wait afterward.
   */
  @Test
  public void a_child_job_launched_from_its_parents_accept_reaches_a_terminal_status()
      throws Exception {
    CountDownLatch parentCanFinish = new CountDownLatch(1);
    AtomicReference<Job> childRef = new AtomicReference<>();

    JobContextConsumer parentJob =
        c -> {
          Job child = jobService.runAsJob(TestJob.builder().build());
          childRef.set(child);
          parentCanFinish.await();
        };

    Job parent = jobService.runAsJob(parentJob);

    await().atMost(Duration.ofSeconds(10)).until(() -> childRef.get() != null);
    Job child = childRef.get();

    // give the child's own worker thread time to run its (fast, sleepMs=0) body to completion
    // and attempt its own status transitions, all while the parent -- and the child's own
    // initial INSERT, joined to it -- is still uncommitted
    Thread.sleep(500);

    parentCanFinish.countDown();
    assertEquals("complete", awaitFinished(parent.getId()).getStatus());

    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                "complete"
                    .equals(jobsRepository.findById(child.getId()).orElseThrow().getStatus()));
    assertEquals("Hello World! i=0\nGoodbye from TestJob!", jobService.getJobLogs(child.getId()));
  }
}
