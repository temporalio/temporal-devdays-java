package io.temporal.devdays.essentials.features;

import io.temporal.activity.ActivityInterface;

/** The Activity the Priority and Fairness slide schedules (22-more-features.md). */
@ActivityInterface
public interface OrderActivities {
  String chargePayment(Order order);
}
