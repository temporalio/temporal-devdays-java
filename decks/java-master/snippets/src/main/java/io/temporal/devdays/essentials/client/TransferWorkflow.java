package io.temporal.devdays.essentials.client;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** The interface the Client in 07b-client.md builds its stub from. Not on a slide. */
@WorkflowInterface
public interface TransferWorkflow {
  @WorkflowMethod
  Receipt transfer(Transfer transfer);
}
