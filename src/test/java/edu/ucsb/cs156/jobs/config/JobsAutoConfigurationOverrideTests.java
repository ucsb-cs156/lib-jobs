package edu.ucsb.cs156.jobs.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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

    /*
     * Every consuming app keeps a launch controller whose bean name is
     * "jobsController"; the library's controller bean is named
     * "libJobsController" so the two coexist (a name collision would fail
     * startup regardless of @ConditionalOnMissingBean, which matches by type).
     */
    @Bean(name = "jobsController")
    public String jobsController() {
      return "stand-in for an app's own launch controller bean";
    }
  }

  @Autowired ApplicationContext context;

  @Test
  public void app_provided_beans_take_precedence() {
    assertEquals(7, context.getBean(JobRateLimit.class).getRateLimitMs());
    assertFalse(
        context.getBean("jobsExecutor") instanceof DelegatingSecurityContextAsyncTaskExecutor);
  }

  @Test
  public void library_controller_coexists_with_app_bean_named_jobsController() {
    assertEquals(String.class, context.getBean("jobsController").getClass());
    assertInstanceOf(
        edu.ucsb.cs156.jobs.controllers.JobsController.class, context.getBean("libJobsController"));
  }
}
