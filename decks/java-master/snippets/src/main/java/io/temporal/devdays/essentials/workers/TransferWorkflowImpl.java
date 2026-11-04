package io.temporal.devdays.essentials.workers;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/**
 * Not on a slide. Registered by type: the Worker creates one instance per Workflow Execution, so
 * the instance count below is how WorkerAppTest proves it.
 */
public class TransferWorkflowImpl implements TransferWorkflow {
  private final BankActivities activities =
      Workflow.newActivityStub(
          BankActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

  private int calls = 0;

  @Override
  public String transfer(String transferId) {
    calls++;
    return activities.submitTransfer(transferId) + " (call " + calls + " on this instance)";
  }
}
