package io.temporal.devdays.essentials.workers;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Not on a slide. Registered as an instance: this one object serves every Activity Task the Worker
 * runs, so shared state must be thread-safe.
 */
public class BankActivitiesImpl implements BankActivities {
  public final AtomicInteger submitted = new AtomicInteger();

  @Override
  public String submitTransfer(String transferId) {
    submitted.incrementAndGet();
    return transferId + " submitted";
  }
}
