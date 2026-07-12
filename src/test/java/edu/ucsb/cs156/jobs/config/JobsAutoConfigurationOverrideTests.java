package edu.ucsb.cs156.jobs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import edu.ucsb.cs156.jobs.services.JobRateLimit;
import edu.ucsb.cs156.jobs.testapp.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

/** Every library bean is @ConditionalOnMissingBean; an app-provided bean must win. */
@SpringBootTest(
    classes = {TestApplication.class, JobsAutoConfigurationOverrideTests.CustomConfig.class})
public class JobsAutoConfigurationOverrideTests {

  @TestConfiguration
  static class CustomConfig {
    @Bean
    public JobRateLimit jobRateLimit() {
      return new JobRateLimit("7");
    }

    @Bean(name = "jobsExecutor")
    public AsyncTaskExecutor jobsExecutor() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.initialize();
      return executor;
    }
  }

  @Autowired ApplicationContext context;

  @Test
  public void app_provided_beans_take_precedence() {
    assertEquals(7, context.getBean(JobRateLimit.class).getRateLimitMs());
    assertFalse(
        context.getBean("jobsExecutor") instanceof DelegatingSecurityContextAsyncTaskExecutor);
  }
}
