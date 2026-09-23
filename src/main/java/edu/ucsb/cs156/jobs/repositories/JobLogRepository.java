package edu.ucsb.cs156.jobs.repositories;

import edu.ucsb.cs156.jobs.entities.JobLog;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobLogRepository extends JpaRepository<JobLog, Long> {

  /** Full log, chronological order — used to assemble the complete log for one job. */
  List<JobLog> findByJobIdOrderByIdAsc(Long jobId);

  /**
   * The most recent lines, newest first, limited by {@code pageable}'s page size — used for the
   * list/paginated endpoints' preview (see {@link
   * edu.ucsb.cs156.jobs.services.JobService#getJobLogPreview}). Callers that want chronological
   * order must reverse this.
   */
  List<JobLog> findByJobIdOrderByIdDesc(Long jobId, Pageable pageable);

  /** Number of lines logged for one job — used to size the "earlier lines omitted" marker. */
  long countByJobId(Long jobId);

  /** Everything logged since {@code afterId} — the incremental "tail -f" query. */
  List<JobLog> findByJobIdAndIdGreaterThanOrderByIdAsc(Long jobId, Long afterId);
}
