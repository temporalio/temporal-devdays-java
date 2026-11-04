package io.temporal.devdays.essentials.updates;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * Records every amount it debits, so the test can see which Updates reached the handler, and can be
 * held shut so the test controls when it finishes.
 */
public class AccountActivitiesImpl implements AccountActivities {
  public final List<Integer> debited = new CopyOnWriteArrayList<>();
  private volatile CountDownLatch gate = new CountDownLatch(0);

  /** Holds every debitAccount call until {@link #release()}. */
  public void hold() {
    gate = new CountDownLatch(1);
  }

  public void release() {
    gate.countDown();
  }

  @Override
  public void debitAccount(int amount) {
    try {
      gate.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
    debited.add(amount);
  }
}
