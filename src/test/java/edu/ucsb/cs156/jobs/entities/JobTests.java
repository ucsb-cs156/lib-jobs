package edu.ucsb.cs156.jobs.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class JobTests {

  @Test
  public void builder_sets_fields() {
    Job job =
        Job.builder()
            .createdById(42L)
            .createdByEmail("test@example.org")
            .jobName("TestJob")
            .status("running")
            .scopeType("course")
            .scopeId(17L)
            .log("line1")
            .build();

    assertEquals(42L, job.getCreatedById());
    assertEquals("test@example.org", job.getCreatedByEmail());
    assertEquals("TestJob", job.getJobName());
    assertEquals("running", job.getStatus());
    assertEquals("course", job.getScopeType());
    assertEquals(17L, job.getScopeId());
    assertEquals("line1", job.getLog());
    assertNull(job.getCreatedAt());
    assertNull(job.getUpdatedAt());
  }

  @Test
  public void onCreate_sets_both_timestamps_equal() {
    Job job = Job.builder().build();
    job.onCreate();
    assertNotNull(job.getCreatedAt());
    assertEquals(job.getCreatedAt(), job.getUpdatedAt());
  }

  @Test
  public void onUpdate_advances_updatedAt_only() {
    Job job = Job.builder().build();
    job.onCreate();
    var createdAt = job.getCreatedAt();
    job.onUpdate();
    assertEquals(createdAt, job.getCreatedAt());
    assertNotNull(job.getUpdatedAt());
    assertFalse(job.getUpdatedAt().isBefore(createdAt));
  }
}
