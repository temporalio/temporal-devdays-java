package io.temporal.devdays.essentials.continueasnew;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** A subscription that bills every month, with no end date. */
@WorkflowInterface
public interface SubscriptionWorkflow {
  @WorkflowMethod
  void run(SubscriptionInput input);
}
