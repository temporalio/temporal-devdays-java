package io.temporal.devdays.essentials.features;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ReportWorkflowImpl implements ReportWorkflow {
  private final ReportActivities activities =
      Workflow.newActivityStub(
          ReportActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(1)).build());

  @Override
  public String buildDailyReport() {
    return activities.buildReport();
  }
}
