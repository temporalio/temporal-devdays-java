package io.temporal.devdays.essentials.features;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderWorkflow {
  @WorkflowMethod
  String processOrder(Order order);
}
