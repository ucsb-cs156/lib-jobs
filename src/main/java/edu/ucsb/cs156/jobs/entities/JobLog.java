package edu.ucsb.cs156.jobs.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One line of a {@link Job}'s log, stored as its own row rather than appended into a single growing
 * TEXT column on {@code jobs}. Each {@code ctx.log()} call is an INSERT here (O(1)), instead of a
 * read-modify-write of the whole accumulated log (O(N) per call, O(N^2) total across N lines) —
 * Postgres and MySQL both rewrite a TEXT column's full new value on every UPDATE under MVCC, so
 * there is no way to make "append to one big column" cheap at the database layer either.
 *
 * <p>{@code id} is a real, ordered, per-line sequence, which is what makes incremental "tail -f"
 * style fetching possible: a client can ask for everything with {@code id > lastSeenId}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Entity(name = "job_logs")
public class JobLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private long id;

  @Column(name = "job_id")
  private Long jobId;

  @Column(columnDefinition = "TEXT")
  private String message;

  private ZonedDateTime createdAt;

  @PrePersist
  void onCreate() {
    createdAt = ZonedDateTime.now();
  }
}
