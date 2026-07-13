package edu.ucsb.cs156.jobs.config;

import edu.ucsb.cs156.jobs.controllers.JobsController;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import edu.ucsb.cs156.jobs.services.JobContextFactory;
import edu.ucsb.cs156.jobs.services.JobRateLimit;
import edu.ucsb.cs156.jobs.services.JobService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Auto-configuration for the jobs system; activates when the library is on the classpath.
 *
 * <p>{@code @AutoConfigurationPackage} adds {@code edu.ucsb.cs156.jobs} to the app's
 * auto-configuration packages, so Spring Boot's default entity and Spring Data repository scanning
 * picks up {@code Job} and {@code JobsRepository} *in addition to* the app's own classes. (Using
 * {@code @EntityScan}/{@code @EnableJpaRepositories} here instead would disable that default
 * scanning and break the consuming app's own entities and repositories.)
 *
 * <p>Every bean is {@code @ConditionalOnMissingBean} so an app can override any piece.
 *
 * <p>The {@code jobsExecutor} defaults to a single thread with an unbounded FIFO queue: jobs
 * execute strictly one at a time, in submission order, which prevents two concurrent jobs from
 * racing each other (e.g. double-launching a sync inserting the same rows). Known limitation: the
 * queue is in-memory, so jobs still queued when the app shuts down are lost and their Job rows
 * remain in "running" status. Apps that want concurrency set {@code app.jobs.core-pool-size} /
 * {@code app.jobs.max-pool-size} / {@code app.jobs.queue-capacity}.
 */
@AutoConfiguration
@AutoConfigurationPackage(basePackages = "edu.ucsb.cs156.jobs")
@EnableAsync
@EnableScheduling
public class JobsAutoConfiguration {

  /*
   * Bean method names are prefixed with libJobs so the resulting bean NAMES
   * cannot collide with beans the consuming apps define themselves: every app
   * keeps a launch controller class named JobsController (bean name
   * "jobsController"), and @ConditionalOnMissingBean matches by type, not
   * name, so a name collision would fail startup with
   * BeanDefinitionOverrideException.
   */

  @Bean
  @ConditionalOnMissingBean
  public JobContextFactory libJobsContextFactory(
      JobsRepository jobsRepository, PlatformTransactionManager transactionManager) {
    return new JobContextFactory(jobsRepository, transactionManager);
  }

  @Bean
  @ConditionalOnMissingBean
  public JobService libJobsService() {
    return new JobService();
  }

  @Bean
  @ConditionalOnMissingBean
  public JobsController libJobsController() {
    return new JobsController();
  }

  @Bean
  @ConditionalOnMissingBean
  public JobRateLimit libJobsRateLimit(
      @Value("${app.jobs.rate-limit-ms:200}") String rateLimitMsString) {
    return new JobRateLimit(rateLimitMsString);
  }

  @Bean(name = "jobsExecutor")
  @ConditionalOnMissingBean(name = "jobsExecutor")
  public AsyncTaskExecutor jobsExecutor(
      @Value("${app.jobs.core-pool-size:1}") int corePoolSize,
      @Value("${app.jobs.max-pool-size:1}") int maxPoolSize,
      @Value("${app.jobs.queue-capacity:2147483647}") int queueCapacity) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    executor.setThreadNamePrefix("jobsExecutor-");
    executor.initialize();
    // propagate the launching admin's security context onto the job thread
    return new DelegatingSecurityContextAsyncTaskExecutor(executor);
  }
}
