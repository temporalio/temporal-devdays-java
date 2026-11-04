package io.temporal.devdays.essentials.heartbeat;

import io.temporal.activity.ActivityCancellationType;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.List;

public class ProcessWorkflowImpl implements ProcessWorkflow {
  // WAIT_CANCELLATION_COMPLETED: when the Workflow is cancelled, it waits for the Activity to
  // finish its cleanup before it ends.
  private final ProcessActivities activities = Workflow.newActivityStub(
      ProcessActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofMinutes(5))
          .setHeartbeatTimeout(Duration.ofSeconds(1))
          .setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)
          .build());

  @Override
  public void run(List<String> items) {
    activities.processItems(items);
  }
}
