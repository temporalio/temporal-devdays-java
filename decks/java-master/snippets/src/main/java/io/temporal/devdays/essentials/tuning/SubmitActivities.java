package io.temporal.devdays.essentials.tuning;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface SubmitActivities {
  String submitOrder(String orderId);
}
