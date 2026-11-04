package io.temporal.devdays.essentials.features;

public class OrderActivitiesImpl implements OrderActivities {
  @Override
  public String chargePayment(Order order) {
    return "charged " + order.id() + " for " + order.tenantId();
  }
}
