package io.temporal.devdays.essentials.updates;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import java.time.Duration;

// The slide shows the class without the Activity stub field: the two parts
// below, joined by one blank line, are the slide.
//
// part: 11-updates.md "Java Updates: the implementation" 1
public class AccountWorkflowImpl implements AccountWorkflow {
// end part
  private final AccountActivities activities =
      Workflow.newActivityStub(
          AccountActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

  // part: 11-updates.md "Java Updates: the implementation" 2
  private int balance;

  @WorkflowInit
  public AccountWorkflowImpl(int opening) {
    balance = opening;
  }

  @Override
  public void run(int opening) {
    Workflow.await(() -> balance == 0);
  }

  @Override
  public int withdraw(int amount) {
    activities.debitAccount(amount);
    balance -= amount;
    return balance;
  }

  @Override
  public void validateWithdraw(int amount) {
    if (amount > balance) {
      throw new IllegalArgumentException("Insufficient funds: balance is " + balance);
    }
  }
}
  // end part
