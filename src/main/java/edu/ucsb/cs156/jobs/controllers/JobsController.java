package edu.ucsb.cs156.jobs.controllers;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.errors.EntityNotFoundException;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import edu.ucsb.cs156.jobs.services.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin REST API for job records: list (all or paginated), fetch one, fetch logs, delete one or
 * all. Launch endpoints stay in each app's own controllers. Requires {@code ROLE_ADMIN}, which
 * consuming apps must support via method security ({@code @EnableMethodSecurity}).
 */
@Tag(name = "Jobs")
@RequestMapping("/api/jobs")
@RestController
@Slf4j
public class JobsController {
  public static final List<String> ALLOWED_SORT_FIELDS =
      Arrays.asList("id", "jobName", "status", "createdByEmail", "createdAt", "updatedAt");

  @Autowired private JobsRepository jobsRepository;

  @Autowired private JobService jobService;

  @Operation(summary = "List all jobs")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping("/all")
  public Iterable<Job> allJobs() {
    return jobsRepository.findAllByOrderByIdDesc();
  }

  @Operation(summary = "Get paginated jobs")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping(value = "/paginated", produces = "application/json")
  public Page<Job> paginatedJobs(
      @Parameter(name = "page", description = "what page of the data", example = "0") @RequestParam
          int page,
      @Parameter(name = "pageSize", description = "size of each page", example = "10") @RequestParam
          int pageSize,
      @Parameter(name = "sortField", description = "sort field", example = "createdAt")
          @RequestParam(defaultValue = "status")
          String sortField,
      @Parameter(name = "sortDirection", description = "sort direction", example = "ASC")
          @RequestParam(defaultValue = "DESC")
          String sortDirection) {

    if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
      throw new IllegalArgumentException(
          String.format(
              "%s is not a valid sort field. Valid values are %s", sortField, ALLOWED_SORT_FIELDS));
    }

    List<String> allowedSortDirections = Arrays.asList("ASC", "DESC");
    if (!allowedSortDirections.contains(sortDirection)) {
      throw new IllegalArgumentException(
          String.format(
              "%s is not a valid sort direction. Valid values are %s",
              sortDirection, allowedSortDirections));
    }

    Direction sortDirectionObject = Direction.DESC;
    if (sortDirection.equals("ASC")) {
      sortDirectionObject = Direction.ASC;
    }

    PageRequest pageRequest = PageRequest.of(page, pageSize, sortDirectionObject, sortField);
    return jobsRepository.findAll(pageRequest);
  }

  @Operation(summary = "Get a specific job by ID if it is in the database")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping("")
  public Job getJobById(
      @Parameter(name = "id", description = "ID of the job") @RequestParam Long id) {
    return jobsRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException(Job.class, id));
  }

  @Operation(summary = "Get long job logs")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping("/logs/{id}")
  public String getJobLogs(@Parameter(name = "id", description = "Job ID") @PathVariable Long id) {
    return jobService.getJobLogs(id);
  }

  @Operation(summary = "Delete specific job record")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @DeleteMapping("")
  public Map<String, String> deleteJob(@Parameter(name = "id") @RequestParam Long id) {
    if (!jobsRepository.existsById(id)) {
      return Map.of("message", String.format("Job with id %d not found", id));
    }
    jobsRepository.deleteById(id);
    return Map.of("message", String.format("Job with id %d deleted", id));
  }

  @Operation(summary = "Delete all job records")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @DeleteMapping("/all")
  public Map<String, String> deleteAllJobs() {
    jobsRepository.deleteAll();
    return Map.of("message", "All jobs deleted");
  }

  /**
   * The apps map these exceptions in an app-level base controller; the library controller cannot
   * extend that, so it carries its own handlers with the same response shape.
   */
  @ExceptionHandler({EntityNotFoundException.class})
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Object handleEntityNotFoundException(Throwable e) {
    return Map.of(
        "type", e.getClass().getSimpleName(),
        "message", e.getMessage());
  }

  @ExceptionHandler({IllegalArgumentException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Object handleIllegalArgument(Throwable e) {
    return Map.of(
        "type", e.getClass().getSimpleName(),
        "message", e.getMessage());
  }
}
