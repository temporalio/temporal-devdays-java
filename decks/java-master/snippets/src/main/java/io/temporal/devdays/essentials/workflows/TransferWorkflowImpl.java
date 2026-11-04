package io.temporal.devdays.essentials.workflows;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;

// slide: 05-workflows.md "heading: Java Workflow implementation"
// TransferWorkflowImpl.java
public class TransferWorkflowImpl implements TransferWorkflow {
  private final BankActivities fraud = Workflow.newActivityStub(BankActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(5))
          .build());
  private final BankActivities bank = Workflow.newActivityStub(BankActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(30))
          .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(5).build())
          .build());

  @Override
  public Receipt transfer(Transfer transfer) {
    if (fraud.checkFraud(transfer)) {
      return Receipt.rejected("flagged by the fraud check");
    }

    bank.withdraw(transfer);
    try {
      bank.deposit(transfer);
    } catch (ActivityFailure e) {
      bank.refund(transfer);
      return Receipt.refunded(e.getMessage());
    }
    return Receipt.completed();
  }
}
// end slide
