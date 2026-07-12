package edu.ucsb.cs156.jobs.testapp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves "/" so the 18-validate-db-schema CI workflow (which boots {@link TestApplication} with
 * {@code ddl-auto=validate} and curls the home page) gets a 200 once startup succeeds.
 */
@RestController
public class HomeController {
  @GetMapping("/")
  public String home() {
    return "lib-jobs TestApplication is running";
  }
}
