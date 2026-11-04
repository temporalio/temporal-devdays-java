package io.temporal.devdays.essentials.observability;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface OrderActivities {
  String chargeCard(String orderId);
}
