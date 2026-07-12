package edu.ucsb.cs156.jobs.testapp;

import edu.ucsb.cs156.jobs.services.JobContext;
import edu.ucsb.cs156.jobs.services.JobContextConsumer;
import lombok.Builder;

/**
 * Reference job implementation, equivalent to the {@code TestJob} each app repo carries today: logs
 * a line {@code count} times (sleeping {@code sleepMs} between lines), then either fails or says
 * goodbye.
 */
@Builder
public class TestJob implements JobContextConsumer {
  @Builder.Default private int count = 1;

  @Builder.Default private long sleepMs = 0;

  @Builder.Default private boolean fail = false;

  private String scopeType;

  private Long scopeId;

  @Override
  public void accept(JobContext c) throws Exception {
    for (int i = 0; i < count; i++) {
      c.log("Hello World! i=" + i);
      Thread.sleep(sleepMs);
    }
    if (fail) {
      throw new Exception("Fail!");
    }
    c.log("Goodbye from TestJob!");
  }

  @Override
  public String getScopeType() {
    return scopeType;
  }

  @Override
  public Long getScopeId() {
    return scopeId;
  }
}
