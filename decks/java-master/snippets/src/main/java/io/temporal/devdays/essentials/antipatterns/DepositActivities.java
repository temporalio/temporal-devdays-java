package io.temporal.devdays.essentials.antipatterns;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface DepositActivities {
  /** ❌ Waits out the hold inside the Activity. On the slide; never scheduled by a test. */
  void releaseAfterHold(Deposit deposit) throws InterruptedException;

  /** ✅ Releases a deposit whose hold the Workflow has already waited out. */
  void releaseFunds(String depositId);
}
