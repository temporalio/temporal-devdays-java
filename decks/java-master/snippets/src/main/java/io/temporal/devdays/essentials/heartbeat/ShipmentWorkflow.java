package io.temporal.devdays.essentials.heartbeat;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ShipmentWorkflow {
  @WorkflowMethod
  ShipmentResult run(ShipmentInput input);
}
