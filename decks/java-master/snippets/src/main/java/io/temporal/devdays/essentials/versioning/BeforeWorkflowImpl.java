package io.temporal.devdays.essentials.versioning;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** The original Workflow: A, then B. */
public class BeforeWorkflowImpl implements MyWorkflow {
  // slide: 18-versioning.md "Before: the original Workflow"
  private final VersioningActivities activities =
      Workflow.newActivityStub(
          VersioningActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofSeconds(10))
              .build());

  @Override
  public void run() {
    activities.activityA();
    activities.activityB();
  }
  // end slide
}
