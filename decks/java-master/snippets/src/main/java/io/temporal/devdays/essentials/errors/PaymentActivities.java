package io.temporal.devdays.essentials.errors;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface PaymentActivities {
  String chargePayment(Order order);
}
