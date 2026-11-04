package io.temporal.devdays.essentials.signals;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

// part: 09-signals.md "Java Signals" 1
@WorkflowInterface
public interface WireTransferWorkflow {
  @WorkflowMethod
  String transfer(WireTransfer wire);

  @SignalMethod
  void approveWire(String officer);
}
// end part
