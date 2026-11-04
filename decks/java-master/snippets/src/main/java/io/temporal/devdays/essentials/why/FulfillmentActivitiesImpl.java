package io.temporal.devdays.essentials.why;

import static io.temporal.devdays.essentials.why.Services.inventory;
import static io.temporal.devdays.essentials.why.Services.payments;
import static io.temporal.devdays.essentials.why.Services.shipping;

// part: 01-why-temporal.md "The Temporal version" 1
// FulfillmentActivitiesImpl.java
public class FulfillmentActivitiesImpl implements FulfillmentActivities {
  @Override
  public String fulfillOrder(String orderId) {
    inventory.reserve(orderId);
    String chargeId = payments.charge(orderId);
    return shipping.ship(orderId, chargeId);
  }
}
// end part
