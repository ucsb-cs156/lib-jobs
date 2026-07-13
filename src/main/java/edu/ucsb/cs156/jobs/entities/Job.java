package edu.ucsb.cs156.jobs.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA entity for one asynchronous job: who launched it, its status, and a human-readable log that
 * grows while the job runs.
 *
 * <p>The creator is denormalized into {@code createdById}/{@code createdByEmail} (no foreign key)
 * so this entity has no dependency on any app's {@code User} entity; values come from the
 * app-implemented {@link edu.ucsb.cs156.jobs.services.JobUserProvider}.
 *
 * <p>{@code scopeType}/{@code scopeId} optionally associate the job with one app-domain object
 * (e.g. {@code "course"}/{@code 17}); both are null for unscoped jobs.
 *
 * <p>Timestamps are maintained with JPA lifecycle callbacks rather than Spring Data auditing, so
 * the library works whether or not the consuming app enables {@code @EnableJpaAuditing}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Entity(name = "jobs")
public class Job {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @Column(name = "created_by_id")
  private Long createdById;

  private String createdByEmail;

  private String jobName;

  private String status;

  private String scopeType;

  private Long scopeId;

  /**
   * Not persisted as a column of this row (see {@code job_logs}, a separate append-only table, one
   * row per log line, v0.2.0). This field exists purely as a Java-level convenience: {@link
   * edu.ucsb.cs156.jobs.services.JobContext}'s null-repository test seam still accumulates into it
   * in-memory so {@code job.getLog()} keeps working in existing tests, and the controller populates
   * it explicitly (full text or a tail preview, depending on the endpoint) before a {@code Job} is
   * serialized to a client.
   */
  @Transient private String log;

  private ZonedDateTime createdAt;

  private ZonedDateTime updatedAt;

  @PrePersist
  void onCreate() {
    createdAt = ZonedDateTime.now();
    updatedAt = createdAt;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = ZonedDateTime.now();
  }
}
