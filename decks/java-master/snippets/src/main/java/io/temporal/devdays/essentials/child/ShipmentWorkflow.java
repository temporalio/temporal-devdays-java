package io.temporal.devdays.essentials.child;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * The child. Nothing marks it as a child: it is an ordinary Workflow, and it is a child only
 * because a parent starts it.
 */
@WorkflowInterface
public interface ShipmentWorkflow {
  @WorkflowMethod
  String ship(String orderId);
}
