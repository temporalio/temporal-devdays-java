package io.temporal.devdays.essentials.updates;

import io.temporal.activity.ActivityInterface;

/** The Activity the Update handler in 11-updates.md calls. */
@ActivityInterface
public interface AccountActivities {
  void debitAccount(int amount);
}
