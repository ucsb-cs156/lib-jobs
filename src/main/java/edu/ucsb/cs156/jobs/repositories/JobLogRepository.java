package edu.ucsb.cs156.jobs.repositories;

import edu.ucsb.cs156.jobs.entities.JobLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobLogRepository extends JpaRepository<JobLog, Long> {

  /** Full log, chronological order — used to assemble the complete log for one job. */
  List<JobLog> findByJobIdOrderByIdAsc(Long jobId);

  /**
   * The most recent lines, newest first — used for the list/paginated endpoints' preview. Callers
   * that want chronological order must reverse this.
   */
  List<JobLog> findTop10ByJobIdOrderByIdDesc(Long jobId);

  /** Everything logged since {@code afterId} — the incremental "tail -f" query. */
  List<JobLog> findByJobIdAndIdGreaterThanOrderByIdAsc(Long jobId, Long afterId);
}
