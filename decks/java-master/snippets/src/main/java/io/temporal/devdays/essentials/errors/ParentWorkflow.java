package io.temporal.devdays.essentials.errors;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ParentWorkflow {
  @WorkflowMethod
  String run(Order order);
}
