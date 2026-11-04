package io.temporal.devdays.essentials.observability;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderWorkflow {
  @WorkflowMethod
  String processOrder(String orderId);
}
