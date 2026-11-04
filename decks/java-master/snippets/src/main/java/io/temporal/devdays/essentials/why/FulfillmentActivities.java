package io.temporal.devdays.essentials.why;

import io.temporal.activity.ActivityInterface;

/** The Activity interface behind "The Temporal version" in 01-why-temporal.md. */
@ActivityInterface
public interface FulfillmentActivities {
  String fulfillOrder(String orderId);
}
