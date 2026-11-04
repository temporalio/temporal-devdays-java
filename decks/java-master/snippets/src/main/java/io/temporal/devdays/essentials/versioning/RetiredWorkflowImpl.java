package io.temporal.devdays.essentials.versioning;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** The patch retired: only C is left, and the version check stays. */
public class RetiredWorkflowImpl implements MyWorkflow {
  private final VersioningActivities activities =
      Workflow.newActivityStub(
          VersioningActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

  // slide: 18-versioning.md "Retiring the patch"
  @Override
  public void run() {
    activities.activityA();

    Workflow.getVersion("use-activity-c", 1, 1);
    activities.activityC();
  }
  // end slide
}
