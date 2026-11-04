package io.temporal.devdays.essentials.signals;

import io.temporal.workflow.Workflow;
import java.time.Duration;

/**
 * Not on a slide: backs the speaker note that a Signal handler may block. The handler sleeps on a
 * durable Timer before it changes state, and the Workflow waits for every handler to finish before
 * it returns.
 */
public class AsyncWireTransferWorkflowImpl implements WireTransferWorkflow {
  private String approver = "";

  @Override
  public String transfer(WireTransfer wire) {
    Workflow.await(() -> !approver.isEmpty());
    Workflow.await(() -> Workflow.isEveryHandlerFinished());
    return wire.wireId() + " approved by " + approver;
  }

  @Override
  public void approveWire(String officer) {
    Workflow.sleep(Duration.ofSeconds(1));
    approver = officer;
  }
}
