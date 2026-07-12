package edu.ucsb.cs156.jobs.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class JobRateLimitTests {

  @Test
  public void valid_value_is_used() {
    assertEquals(300, new JobRateLimit("300").getRateLimitMs());
  }

  @Test
  public void zero_is_a_valid_value() {
    assertEquals(0, new JobRateLimit("0").getRateLimitMs());
  }

  @Test
  public void unparseable_value_falls_back_to_default() {
    assertEquals(JobRateLimit.DEFAULT_RATE_LIMIT_MS, new JobRateLimit("abc").getRateLimitMs());
  }

  @Test
  public void negative_value_falls_back_to_default() {
    assertEquals(JobRateLimit.DEFAULT_RATE_LIMIT_MS, new JobRateLimit("-1").getRateLimitMs());
  }

  @Test
  public void sleep_waits_at_least_the_configured_delay() throws InterruptedException {
    JobRateLimit rateLimit = new JobRateLimit("50");
    long start = System.nanoTime();
    rateLimit.sleep();
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
    assertTrue(elapsedMs >= 45, "slept only " + elapsedMs + " ms");
  }
}
