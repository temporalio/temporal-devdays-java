package io.temporal.devdays.essentials.antipatterns;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface TransferWorkflow {
  @WorkflowMethod
  void transfer(Transfer transfer);
}
