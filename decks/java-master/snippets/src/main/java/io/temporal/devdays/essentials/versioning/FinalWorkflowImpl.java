package io.temporal.devdays.essentials.versioning;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** B swapped for C with no version check: the unsafe edit, and the last step of the cleanup. */
public class FinalWorkflowImpl implements MyWorkflow {
  private final VersioningActivities activities =
      Workflow.newActivityStub(
          VersioningActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

  @Override
  public void run() {
    activities.activityA();
    activities.activityC();
  }
}
