package edu.ucsb.cs156.jobs.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class JobContextConsumerTests {

  static class DemoJob implements JobContextConsumer {
    @Override
    public void accept(JobContext c) {}
  }

  @Test
  public void defaults_jobName_to_simple_class_name_and_scope_to_null() {
    DemoJob demoJob = new DemoJob();
    assertEquals("DemoJob", demoJob.getJobName());
    assertNull(demoJob.getScopeType());
    assertNull(demoJob.getScopeId());
  }
}
