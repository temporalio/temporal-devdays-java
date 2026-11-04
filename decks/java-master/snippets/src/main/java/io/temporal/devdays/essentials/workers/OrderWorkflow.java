package io.temporal.devdays.essentials.workers;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** The Workflow the Worker in 07-workers.md registers. Not on a slide. */
@WorkflowInterface
public interface OrderWorkflow {
  @WorkflowMethod
  String processOrder(String orderId);
}
