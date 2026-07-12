package edu.ucsb.cs156.jobs.testapp;

import edu.ucsb.cs156.jobs.services.JobUserProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Minimal consuming application for the library's own tests: the only things a real app must
 * provide are a {@code JobUserProvider} bean and method security (see {@link TestSecurityConfig}).
 */
@SpringBootApplication
public class TestApplication {
  public static void main(String[] args) {
    SpringApplication.run(TestApplication.class, args);
  }

  @Bean
  public JobUserProvider jobUserProvider() {
    return new JobUserProvider() {
      @Override
      public Long getCurrentUserId() {
        return 42L;
      }

      @Override
      public String getCurrentUserEmail() {
        return "test@example.org";
      }
    };
  }
}
