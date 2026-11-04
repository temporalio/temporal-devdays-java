package io.temporal.devdays.essentials.tuning;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface SubmitWorkflow {
  @WorkflowMethod
  String submit(String orderId);
}
