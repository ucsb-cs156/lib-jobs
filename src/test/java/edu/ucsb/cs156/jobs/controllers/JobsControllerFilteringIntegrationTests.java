package edu.ucsb.cs156.jobs.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import edu.ucsb.cs156.jobs.testapp.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end proof that each {@code /paginated} filter param actually narrows the query, against a
 * real {@link JobsRepository}/H2 database. {@link JobsControllerTests} mocks {@code
 * jobsRepository}, so the {@code Specification} predicate lambdas built in {@link
 * JobsController#paginatedJobs} are constructed there but never invoked by a real {@code
 * CriteriaBuilder} — this class is what actually exercises them.
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
public class JobsControllerFilteringIntegrationTests {

  @Autowired MockMvc mockMvc;

  @Autowired JobsRepository jobsRepository;

  @BeforeEach
  public void cleanSlate() {
    jobsRepository.deleteAll();
  }

  private void seed() {
    jobsRepository.save(
        Job.builder()
            .jobName("SyncCourseWithPlRepoJob")
            .status("running")
            .createdByEmail("cgaucho@ucsb.edu")
            .scopeType("course")
            .scopeId(17L)
            .build());
    jobsRepository.save(
        Job.builder()
            .jobName("GradeHistoryImportJob")
            .status("complete")
            .createdByEmail("ldelplaya@ucsb.edu")
            .scopeType("course")
            .scopeId(42L)
            .build());
    jobsRepository.save(
        Job.builder()
            .jobName("UnrelatedCleanupJob")
            .status("complete")
            .createdByEmail("cgaucho@ucsb.edu")
            .build());
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void filters_by_exact_status() throws Exception {
    seed();

    mockMvc
        .perform(get("/api/jobs/paginated?page=0&pageSize=10&status=running"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].jobName").value("SyncCourseWithPlRepoJob"));
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void filters_by_jobName_case_insensitive_substring() throws Exception {
    seed();

    mockMvc
        .perform(get("/api/jobs/paginated?page=0&pageSize=10&jobName=course"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].jobName").value("SyncCourseWithPlRepoJob"));
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void filters_by_createdByEmail_case_insensitive_substring() throws Exception {
    seed();

    mockMvc
        .perform(get("/api/jobs/paginated?page=0&pageSize=10&createdByEmail=CGAUCHO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void filters_by_scopeType() throws Exception {
    seed();

    mockMvc
        .perform(get("/api/jobs/paginated?page=0&pageSize=10&scopeType=course"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void filters_by_scopeId() throws Exception {
    seed();

    mockMvc
        .perform(get("/api/jobs/paginated?page=0&pageSize=10&scopeId=17"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].jobName").value("SyncCourseWithPlRepoJob"));
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void combines_multiple_filters_as_an_and() throws Exception {
    seed();

    mockMvc
        .perform(
            get("/api/jobs/paginated?page=0&pageSize=10&createdByEmail=cgaucho&scopeType=course"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].jobName").value("SyncCourseWithPlRepoJob"));
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void no_filters_returns_everything() throws Exception {
    seed();

    mockMvc
        .perform(get("/api/jobs/paginated?page=0&pageSize=10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void empty_string_filter_params_are_treated_as_absent() throws Exception {
    seed();

    mockMvc
        .perform(
            get(
                "/api/jobs/paginated?page=0&pageSize=10&status=&jobName=&createdByEmail="
                    + "&scopeType="))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(3));
  }
}
