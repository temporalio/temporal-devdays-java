package io.temporal.devdays.essentials.signals;

import io.temporal.workflow.Workflow;

// part: 09-signals.md "Java Signals" 2
public class WireTransferWorkflowImpl implements WireTransferWorkflow {
  private boolean approved = false;
  private String approver = "";

  @Override
  public String transfer(WireTransfer wire) {
    Workflow.await(() -> approved);
    return wire.wireId() + " approved by " + approver;
  }

  @Override
  public void approveWire(String officer) {
    approved = true;
    approver = officer;
  }
}
// end part
