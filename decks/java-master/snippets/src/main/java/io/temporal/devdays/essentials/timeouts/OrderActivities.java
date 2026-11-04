package io.temporal.devdays.essentials.timeouts;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface OrderActivities {
  String chargePayment(Order order);

  /** The Maximum Attempts diagram: a payment API that keeps returning 503. */
  String chargeFlakyPayment(Order order);

  /** The Non-retryable diagram, with the caller deciding: the failure is retryable on its own. */
  String chargeDeclinedCard(Order order);
}
