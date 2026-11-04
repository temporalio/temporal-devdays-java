package io.temporal.devdays.essentials.resumable;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** The Workflow the Resumable Activity console and demo act out. Workflow Type: TransferWorkflow. */
@WorkflowInterface
public interface TransferWorkflow {
  @WorkflowMethod
  String transfer(Transfer transfer);

  /** A corrected destination account from an operator. Ignored unless it is 8 to 12 digits. */
  @SignalMethod
  void retryWithCorrection(String account);

  /** PENDING, TRANSFERRING or AWAITING_CORRECTION. */
  @QueryMethod
  String getStatus();
}
