package io.temporal.devdays.essentials.antipatterns;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface DebitActivities {
  /** Posts a debit to the ledger and returns the key it was posted under. */
  String postDebit(Debit debit);
}
