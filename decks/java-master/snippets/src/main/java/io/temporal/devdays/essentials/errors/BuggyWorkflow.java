package io.temporal.devdays.essentials.errors;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface BuggyWorkflow {
  @WorkflowMethod
  OrderResult process(Order order);
}
