package io.temporal.devdays.essentials.resumable;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/**
 * Resumable Activity: when the transfer fails, park instead of failing, wait for a Signal with a
 * corrected account, then run the same Activity again in the same Workflow Execution.
 */
public class TransferWorkflowImpl implements TransferWorkflow {
  private final TransferActivities activities = Workflow.newActivityStub(
      TransferActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(30))
          // Three attempts for timeouts and 503s. AccountNotFoundError is non-retryable.
          .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
          .build());

  private String status = "PENDING";
  private String fix;

  @Override
  public String transfer(Transfer transfer) {
    String account = transfer.toAccount();
    while (true) {
      status = "TRANSFERRING";
      try {
        return activities.executeTransfer(transfer.withToAccount(account));
      } catch (ActivityFailure e) {
        // Let cancellation through: only a failed transfer parks.
        if (e.getCause() instanceof CanceledFailure) {
          throw e;
        }
        status = "AWAITING_CORRECTION";
        Workflow.await(() -> fix != null);
        account = fix;
        fix = null;
      }
    }
  }

  @Override
  public void retryWithCorrection(String account) {
    if (account.matches("\\d{8,12}")) {
      fix = account;
    }
  }

  @Override
  public String getStatus() {
    return status;
  }
}
