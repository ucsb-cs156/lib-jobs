package edu.ucsb.cs156.jobs.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

import edu.ucsb.cs156.jobs.entities.Job;
import edu.ucsb.cs156.jobs.repositories.JobsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class JobContextFactoryTests {

  @Mock JobsRepository jobsRepository;

  @Test
  public void createContext_wires_repository_and_job() {
    JobContextFactory factory = new JobContextFactory(jobsRepository);
    Job job = Job.builder().build();

    JobContext context = factory.createContext(job);

    assertNotNull(context);
    context.log("hello");
    assertEquals("hello", job.getLog());
    verify(jobsRepository).save(job);
  }
}
