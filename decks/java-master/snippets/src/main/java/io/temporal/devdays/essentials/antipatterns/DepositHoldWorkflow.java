package io.temporal.devdays.essentials.antipatterns;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface DepositHoldWorkflow {
  @WorkflowMethod
  void hold(Deposit deposit);
}
