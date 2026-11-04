package io.temporal.devdays.essentials.updates;

import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import io.temporal.workflow.WorkflowLock;
import java.time.Duration;

/**
 * Not on a slide: the fix the speaker notes in 11-updates.md describe for two concurrent
 * withdraw(80) calls against a balance of 100 that both pass the validator. A WorkflowLock lets one
 * handler run at a time, and the handler re-checks once it holds the lock. Throwing
 * ApplicationFailure fails only that Update, not the Workflow.
 */
public class LockedAccountWorkflowImpl implements AccountWorkflow {
  private final AccountActivities activities =
      Workflow.newActivityStub(
          AccountActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

  private final WorkflowLock lock = Workflow.newWorkflowLock();
  private int balance;

  @WorkflowInit
  public LockedAccountWorkflowImpl(int opening) {
    balance = opening;
  }

  @Override
  public void run(int opening) {
    Workflow.await(() -> balance == 0);
  }

  @Override
  public int withdraw(int amount) {
    lock.lock();
    try {
      if (amount > balance) {
        throw ApplicationFailure.newNonRetryableFailure(
            "Insufficient funds: balance is " + balance, "InsufficientFunds");
      }
      activities.debitAccount(amount);
      balance -= amount;
      return balance;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void validateWithdraw(int amount) {
    if (amount > balance) {
      throw new IllegalArgumentException("Insufficient funds: balance is " + balance);
    }
  }
}
