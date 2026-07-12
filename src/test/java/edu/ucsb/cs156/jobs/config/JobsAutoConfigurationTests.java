package edu.ucsb.cs156.jobs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import edu.ucsb.cs156.jobs.controllers.JobsController;
import edu.ucsb.cs156.jobs.services.JobContextFactory;
import edu.ucsb.cs156.jobs.services.JobRateLimit;
import edu.ucsb.cs156.jobs.services.JobService;
import edu.ucsb.cs156.jobs.testapp.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(classes = TestApplication.class)
public class JobsAutoConfigurationTests {

  @Autowired ApplicationContext context;

  @Test
  public void library_beans_are_auto_configured() {
    assertNotNull(context.getBean(JobService.class));
    assertNotNull(context.getBean(JobsController.class));
    assertNotNull(context.getBean(JobContextFactory.class));
    assertNotNull(context.getBean(JobRateLimit.class));
  }

  @Test
  public void rate_limit_defaults_to_200ms() {
    assertEquals(200, context.getBean(JobRateLimit.class).getRateLimitMs());
  }

  @Test
  public void jobsExecutor_is_single_threaded_and_security_context_aware_by_default() {
    Object executor = context.getBean("jobsExecutor");
    assertInstanceOf(DelegatingSecurityContextAsyncTaskExecutor.class, executor);

    ThreadPoolTaskExecutor delegate =
        (ThreadPoolTaskExecutor) ReflectionTestUtils.getField(executor, "delegate");
    assertEquals(1, delegate.getCorePoolSize());
    assertEquals(1, delegate.getMaxPoolSize());
    assertEquals(Integer.MAX_VALUE, delegate.getQueueCapacity());
    assertEquals("jobsExecutor-", ReflectionTestUtils.getField(delegate, "threadNamePrefix"));
  }
}
