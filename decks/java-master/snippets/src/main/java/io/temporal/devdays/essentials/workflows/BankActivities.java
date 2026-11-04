package io.temporal.devdays.essentials.workflows;

import io.temporal.activity.ActivityInterface;

/** The Activities the transfer Workflow calls. Not on a slide. */
@ActivityInterface
public interface BankActivities {
  /** True when the transfer looks fraudulent. */
  boolean checkFraud(Transfer transfer);

  void withdraw(Transfer transfer);

  void deposit(Transfer transfer);

  void refund(Transfer transfer);
}
