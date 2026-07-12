package edu.ucsb.cs156.jobs.repositories;

import edu.ucsb.cs156.jobs.entities.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for {@link Job} entities. Paging comes from {@link JpaRepository};
 * scope-based queries support apps that associate jobs with a domain object (see {@code
 * Job.scopeType}/{@code Job.scopeId}).
 */
@Repository
public interface JobsRepository extends JpaRepository<Job, Long> {
  Iterable<Job> findAllByOrderByIdDesc();

  Iterable<Job> findByScopeTypeAndScopeIdOrderByIdDesc(String scopeType, Long scopeId);

  @Transactional
  void deleteByScopeTypeAndScopeId(String scopeType, Long scopeId);
}
