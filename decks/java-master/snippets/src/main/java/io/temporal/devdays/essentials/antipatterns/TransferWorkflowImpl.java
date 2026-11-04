package io.temporal.devdays.essentials.antipatterns;

import io.temporal.workflow.Workflow;
import java.time.Duration;

/** Stays running long enough for a second start with the same ID to collide with it. */
public class TransferWorkflowImpl implements TransferWorkflow {
  @Override
  public void transfer(Transfer transfer) {
    Workflow.sleep(Duration.ofDays(1));
  }
}
