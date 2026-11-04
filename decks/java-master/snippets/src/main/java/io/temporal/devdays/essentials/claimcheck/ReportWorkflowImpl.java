package io.temporal.devdays.essentials.claimcheck;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ReportWorkflowImpl implements ReportWorkflow {
  private final ReportActivities activities =
      Workflow.newActivityStub(
          ReportActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

  @Override
  public int reportLength() {
    return activities.buildReport().length;
  }
}
