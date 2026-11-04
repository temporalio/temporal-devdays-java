package io.temporal.devdays.essentials.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// slide: 25-observability.md "Logging from an Activity"
public class OrderActivitiesImpl implements OrderActivities {
  private static final Logger log = LoggerFactory.getLogger(OrderActivitiesImpl.class);

  @Override
  public String chargeCard(String orderId) {
    log.info("Charging card: {}", orderId);
    String receipt = "receipt-" + orderId;
    log.info("Card charged: {}", receipt);
    return receipt;
  }
}
// end slide
