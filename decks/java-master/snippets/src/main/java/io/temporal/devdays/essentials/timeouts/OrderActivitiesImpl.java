package io.temporal.devdays.essentials.timeouts;

import io.temporal.failure.ApplicationFailure;

public class OrderActivitiesImpl implements OrderActivities {
  @Override
  public String chargePayment(Order order) {
    return "charged-" + order.orderId();
  }

  @Override
  public String chargeFlakyPayment(Order order) {
    throw new IllegalStateException("payment API returned 503");
  }

  @Override
  public String chargeDeclinedCard(Order order) {
    throw ApplicationFailure.newFailure("card declined", "CardDeclined");
  }
}
