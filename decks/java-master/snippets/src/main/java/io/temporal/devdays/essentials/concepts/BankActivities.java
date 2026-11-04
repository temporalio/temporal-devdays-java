package io.temporal.devdays.essentials.concepts;

import io.temporal.activity.ActivityInterface;

/** The Activities the Worker in 03-temporal-concepts.md "One Worker, both kinds of code" runs. */
@ActivityInterface
public interface BankActivities {
  String withdraw(String transferId);

  String deposit(String transferId);
}
