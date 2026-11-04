package io.temporal.devdays.essentials.startexecute;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Waits for a person: the shape that needs WorkflowClient.start. Not on a slide. */
@WorkflowInterface
public interface ApprovalWorkflow {
  @WorkflowMethod
  OrderResult run(Order order);

  @QueryMethod
  String getStatus();

  @SignalMethod
  void approve(String approver);
}
