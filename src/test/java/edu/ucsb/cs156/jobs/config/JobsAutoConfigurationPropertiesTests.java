package edu.ucsb.cs156.jobs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.ucsb.cs156.jobs.services.JobRateLimit;
import edu.ucsb.cs156.jobs.testapp.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
    classes = TestApplication.class,
    properties = {
      "app.jobs.rate-limit-ms=7",
      "app.jobs.core-pool-size=2",
      "app.jobs.max-pool-size=3",
      "app.jobs.queue-capacity=44"
    })
public class JobsAutoConfigurationPropertiesTests {

  @Autowired ApplicationContext context;

  @Test
  public void properties_configure_rate_limit_and_executor() {
    assertEquals(7, context.getBean(JobRateLimit.class).getRateLimitMs());

    Object executor = context.getBean("jobsExecutor");
    ThreadPoolTaskExecutor delegate =
        (ThreadPoolTaskExecutor) ReflectionTestUtils.getField(executor, "delegate");
    assertEquals(2, delegate.getCorePoolSize());
    assertEquals(3, delegate.getMaxPoolSize());
    assertEquals(44, delegate.getQueueCapacity());
  }
}
