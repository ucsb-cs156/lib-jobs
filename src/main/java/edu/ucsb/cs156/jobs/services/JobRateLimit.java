package edu.ucsb.cs156.jobs.services;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Configurable delay for rate limiting calls to external APIs from inside jobs. Configured via the
 * {@code app.jobs.rate-limit-ms} property; invalid or negative values fall back to a 200 ms default
 * with a warning.
 */
@Slf4j
public class JobRateLimit {
  public static final int DEFAULT_RATE_LIMIT_MS = 200;

  @Getter private int rateLimitMs;

  public JobRateLimit(String rateLimitMsString) {
    try {
      rateLimitMs = Integer.parseInt(rateLimitMsString);
      if (rateLimitMs < 0) {
        throw new NumberFormatException("negative value");
      }
    } catch (NumberFormatException e) {
      rateLimitMs = DEFAULT_RATE_LIMIT_MS;
      log.warn(
          "Invalid app.jobs.rate-limit-ms value: {}; using default of {} ms",
          rateLimitMsString,
          rateLimitMs);
    }
  }

  public void sleep() throws InterruptedException {
    Thread.sleep(rateLimitMs);
  }
}
