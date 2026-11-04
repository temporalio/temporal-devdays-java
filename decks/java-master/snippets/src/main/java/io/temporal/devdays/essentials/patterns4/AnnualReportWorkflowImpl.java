package io.temporal.devdays.essentials.patterns4;

import io.temporal.activity.ActivityOptions;
import io.temporal.devdays.essentials.patterns4.RfiWorkflows.AnnualReportWorkflow;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class AnnualReportWorkflowImpl implements AnnualReportWorkflow {
  // part: 04-design-patterns.md "takes significant time" 2
  // AnnualReportWorkflowImpl.java
  private final RfiActivities activities =
      Workflow.newActivityStub(
          RfiActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofHours(2)).build());

  @Override
  public void run(RfiRequest req) {
    activities.generateAnnualReport(req);
  }
  // end part
}
