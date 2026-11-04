package io.temporal.devdays.essentials.child;

public class ShipmentWorkflowImpl implements ShipmentWorkflow {
  @Override
  public String ship(String orderId) {
    return "TRACK-" + orderId;
  }
}
