package io.temporal.devdays.essentials.heartbeat;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ShipmentWorkflowImpl implements ShipmentWorkflow {
  // The caller's half of Heartbeating: a short Heartbeat Timeout to notice a dead Worker fast,
  // and a Start-To-Close Timeout long enough for the whole shipment.
  private final ShipmentActivities activities = Workflow.newActivityStub(
      ShipmentActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(30))
          .setHeartbeatTimeout(Duration.ofSeconds(10))
          .setRetryOptions(RetryOptions.newBuilder().setInitialInterval(Duration.ofSeconds(1)).build())
          .build());

  @Override
  public ShipmentResult run(ShipmentInput input) {
    return activities.packShipment(input);
  }
}
