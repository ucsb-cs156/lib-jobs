package edu.ucsb.cs156.jobs.repositories;

import edu.ucsb.cs156.jobs.entities.Job;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository for {@link Job} entities. Paging comes from {@link JpaRepository}; {@link
 * JpaSpecificationExecutor} backs {@code /paginated}'s optional filters, composed as a dynamic
 * {@code Specification<Job>} rather than a combinatorial set of derived-query methods for every
 * combination of filters; scope-based queries support apps that associate jobs with a domain object
 * (see {@code Job.scopeType}/{@code Job.scopeId}).
 */
@Repository
public interface JobsRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {
  Iterable<Job> findAllByOrderByIdDesc();

  Iterable<Job> findByScopeTypeAndScopeIdOrderByIdDesc(String scopeType, Long scopeId);

  /** Used by {@link edu.ucsb.cs156.jobs.services.JobService}'s startup orphan recovery sweep. */
  Iterable<Job> findByStatusIn(Collection<String> statuses);

  @Transactional
  void deleteByScopeTypeAndScopeId(String scopeType, Long scopeId);
}
