package io.temporal.devdays.essentials.resumable;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface TransferActivities {
  /** Moves the money, or fails non-retryably if the bank does not know the account. */
  String executeTransfer(Transfer transfer);
}
