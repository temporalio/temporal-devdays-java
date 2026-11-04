package io.temporal.devdays.essentials.concepts;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** Orchestration only: no I/O, just two Activity calls in order. */
public class TransferWorkflowImpl implements TransferWorkflow {
  private final TransferActivities activities =
      Workflow.newActivityStub(
          TransferActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

  @Override
  public String transfer(String transferId) {
    return activities.withdraw(transferId) + "," + activities.deposit(transferId);
  }
}
