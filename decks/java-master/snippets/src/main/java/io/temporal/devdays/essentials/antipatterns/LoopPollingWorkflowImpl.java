package io.temporal.devdays.essentials.antipatterns;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** ❌ Polling in a Workflow loop. */
public class LoopPollingWorkflowImpl implements PollingWorkflow {
  private final WireActivities wires = Workflow.newActivityStub(
      WireActivities.class,
      ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

  @Override
  public void awaitWire(String wireId) {
    // part: 17b-patterns-and-anti-patterns.md "Polling an external system" 1
    // ❌ Every pass adds an Activity and a Timer to history
    String status = wires.checkSettlement(wireId);
    while (!status.equals("SETTLED")) {
      Workflow.sleep(Duration.ofMinutes(1));
      status = wires.checkSettlement(wireId);
    }
    // end part
  }
}
