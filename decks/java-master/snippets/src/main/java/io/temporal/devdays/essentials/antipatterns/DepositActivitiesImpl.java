package io.temporal.devdays.essentials.antipatterns;

import java.time.Duration;

public class DepositActivitiesImpl implements DepositActivities {
  private final Bank bank;

  public DepositActivitiesImpl(Bank bank) {
    this.bank = bank;
  }

  // part: 17b-patterns-and-anti-patterns.md "Sleeping in an Activity" 1
  // ❌ An Activity that waits out a three-day hold
  @Override
  public void releaseAfterHold(Deposit deposit) throws InterruptedException {
    Thread.sleep(Duration.ofDays(3));
    bank.release(deposit.depositId());
  }
  // end part

  @Override
  public void releaseFunds(String depositId) {
    bank.release(depositId);
  }
}
