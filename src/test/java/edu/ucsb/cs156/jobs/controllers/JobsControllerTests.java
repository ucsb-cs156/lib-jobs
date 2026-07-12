package edu.ucsb.cs156.jobs.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.errors.EntityNotFoundException;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import edu.ucsb.cs156.jobs.services.JobService;
import edu.ucsb.cs156.jobs.testapp.TestApplication;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
public class JobsControllerTests {

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper mapper;

  @MockitoBean JobsRepository jobsRepository;

  @MockitoBean JobService jobService;

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void admin_can_list_all_jobs() throws Exception {
    Job job1 = Job.builder().jobName("TestJob").status("complete").build();
    Job job2 = Job.builder().jobName("OtherJob").status("running").build();
    when(jobsRepository.findAllByOrderByIdDesc()).thenReturn(List.of(job1, job2));

    MvcResult response =
        mockMvc.perform(get("/api/jobs/all")).andExpect(status().isOk()).andReturn();

    String expectedJson = mapper.writeValueAsString(List.of(job1, job2));
    assertEquals(expectedJson, response.getResponse().getContentAsString());
  }

  @WithMockUser(roles = {"USER"})
  @Test
  public void non_admin_cannot_list_all_jobs() throws Exception {
    mockMvc.perform(get("/api/jobs/all")).andExpect(status().isForbidden());
  }

  @Test
  public void logged_out_users_cannot_list_all_jobs() throws Exception {
    mockMvc.perform(get("/api/jobs/all")).andExpect(status().isForbidden());
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void admin_can_get_paginated_jobs_with_defaults() throws Exception {
    Job job = Job.builder().jobName("TestJob").status("complete").build();
    Page<Job> page = new PageImpl<>(List.of(job));
    when(jobsRepository.findAll(any(PageRequest.class))).thenReturn(page);

    mockMvc
        .perform(get("/api/jobs/paginated?page=0&pageSize=10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].jobName").value("TestJob"));

    ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
    verify(jobsRepository).findAll(captor.capture());
    PageRequest pageRequest = captor.getValue();
    assertEquals(0, pageRequest.getPageNumber());
    assertEquals(10, pageRequest.getPageSize());
    assertEquals(Sort.by(Sort.Direction.DESC, "status"), pageRequest.getSort());
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void paginated_supports_ascending_sort_on_allowed_field() throws Exception {
    when(jobsRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<Job>(List.of()));

    mockMvc
        .perform(get("/api/jobs/paginated?page=1&pageSize=5&sortField=createdAt&sortDirection=ASC"))
        .andExpect(status().isOk());

    ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
    verify(jobsRepository).findAll(captor.capture());
    assertEquals(Sort.by(Sort.Direction.ASC, "createdAt"), captor.getValue().getSort());
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void paginated_rejects_invalid_sort_field() throws Exception {
    mockMvc
        .perform(get("/api/jobs/paginated?page=0&pageSize=10&sortField=nope"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("IllegalArgumentException"))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "nope is not a valid sort field. Valid values are "
                        + JobsController.ALLOWED_SORT_FIELDS));
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void paginated_rejects_invalid_sort_direction() throws Exception {
    mockMvc
        .perform(get("/api/jobs/paginated?page=0&pageSize=10&sortDirection=SIDEWAYS"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value("SIDEWAYS is not a valid sort direction. Valid values are [ASC, DESC]"));
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void admin_can_get_job_by_id() throws Exception {
    Job job = Job.builder().jobName("TestJob").status("complete").build();
    when(jobsRepository.findById(1L)).thenReturn(Optional.of(job));

    MvcResult response =
        mockMvc.perform(get("/api/jobs?id=1")).andExpect(status().isOk()).andReturn();

    assertEquals(mapper.writeValueAsString(job), response.getResponse().getContentAsString());
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void get_job_by_id_returns_404_when_missing() throws Exception {
    when(jobsRepository.findById(2L)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/jobs?id=2"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("EntityNotFoundException"))
        .andExpect(jsonPath("$.message").value("Job with id 2 not found"));
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void admin_can_get_job_logs() throws Exception {
    when(jobService.getJobLogs(1L)).thenReturn("line1\nline2");

    MvcResult response =
        mockMvc.perform(get("/api/jobs/logs/1")).andExpect(status().isOk()).andReturn();

    assertEquals("line1\nline2", response.getResponse().getContentAsString());
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void job_logs_returns_404_when_missing() throws Exception {
    when(jobService.getJobLogs(2L)).thenThrow(new EntityNotFoundException(Job.class, 2L));

    mockMvc
        .perform(get("/api/jobs/logs/2"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Job with id 2 not found"));
  }

  @WithMockUser(roles = {"USER"})
  @Test
  public void non_admin_cannot_get_job_logs() throws Exception {
    mockMvc.perform(get("/api/jobs/logs/1")).andExpect(status().isForbidden());
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void admin_can_delete_a_job() throws Exception {
    when(jobsRepository.existsById(1L)).thenReturn(true);

    mockMvc
        .perform(delete("/api/jobs?id=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Job with id 1 deleted"));

    verify(jobsRepository).deleteById(1L);
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void delete_reports_missing_job() throws Exception {
    when(jobsRepository.existsById(2L)).thenReturn(false);

    mockMvc
        .perform(delete("/api/jobs?id=2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Job with id 2 not found"));
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void admin_can_delete_all_jobs() throws Exception {
    mockMvc
        .perform(delete("/api/jobs/all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("All jobs deleted"));

    verify(jobsRepository).deleteAll();
  }

  @WithMockUser(roles = {"USER"})
  @Test
  public void non_admin_cannot_delete_jobs() throws Exception {
    mockMvc.perform(delete("/api/jobs/all")).andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/jobs?id=1")).andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/jobs/paginated?page=0&pageSize=10"))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/jobs?id=1")).andExpect(status().isForbidden());
  }

  @WithMockUser(roles = {"ADMIN"})
  @Test
  public void all_jobs_returns_empty_list_content_type_json() throws Exception {
    when(jobsRepository.findAllByOrderByIdDesc()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/jobs/all"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }
}
