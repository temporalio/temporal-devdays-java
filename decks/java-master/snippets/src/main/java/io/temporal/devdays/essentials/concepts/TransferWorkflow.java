package io.temporal.devdays.essentials.concepts;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** The Workflow the Worker in 03-temporal-concepts.md "One Worker, both kinds of code" runs. */
@WorkflowInterface
public interface TransferWorkflow {
  @WorkflowMethod
  String transfer(String transferId);
}
