package io.temporal.devdays.essentials.workers;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Not on a slide. Registered as an instance: this one object serves every Activity Task the Worker
 * runs, so shared state must be thread-safe.
 */
public class OrderActivitiesImpl implements OrderActivities {
  public final AtomicInteger submitted = new AtomicInteger();

  @Override
  public String submitOrder(String orderId) {
    submitted.incrementAndGet();
    return orderId + " submitted";
  }
}
