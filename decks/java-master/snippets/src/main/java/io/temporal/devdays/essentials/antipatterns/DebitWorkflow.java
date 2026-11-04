package io.temporal.devdays.essentials.antipatterns;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface DebitWorkflow {
  @WorkflowMethod
  String debit(Debit debit);
}
