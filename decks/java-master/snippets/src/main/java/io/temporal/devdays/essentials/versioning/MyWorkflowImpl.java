package io.temporal.devdays.essentials.versioning;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** The patched Workflow: new executions run C, executions that already started keep B. */
public class MyWorkflowImpl implements MyWorkflow {
  private final VersioningActivities activities =
      Workflow.newActivityStub(
          VersioningActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

  // slide: 18-versioning.md "After: the versioned Workflow"
  @Override
  public void run() {
    activities.activityA();

    int version =
        Workflow.getVersion("use-activity-c", Workflow.DEFAULT_VERSION, 1);
    if (version == 1) {
      activities.activityC();
    } else {
      activities.activityB();
    }
  }
  // end slide
}
