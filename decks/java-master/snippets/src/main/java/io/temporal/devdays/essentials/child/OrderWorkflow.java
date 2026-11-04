package io.temporal.devdays.essentials.child;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** The parent: starts one ShipmentWorkflow child and waits for its tracking number. */
@WorkflowInterface
public interface OrderWorkflow {
  @WorkflowMethod
  String placeOrder(Order order);
}
