package edu.ucsb.cs156.jobs.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

  // 1048576 is 2^20, which is the max size of a mediumtext in MySQL
  @Column(
      columnDefinition = "TEXT",
      length = 1048576) // needed for long strings, i.e. log entries longer than 255
  // characters
  private String log;

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
