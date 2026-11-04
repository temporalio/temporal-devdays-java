package io.temporal.devdays.essentials.errors;

import io.temporal.failure.ApplicationFailure;

public class PaymentActivitiesImpl implements PaymentActivities {

  /** Stands in for a payment gateway client. */
  interface Gateway {
    String charge(Order order);
  }

  private final Gateway gateway = order -> "charged-" + order.orderId();

  // slide: 24-error-handling.md "Raising errors from an Activity"
  @Override
  public String chargePayment(Order order) {
    if (!cardHasFunds(order)) {
      throw ApplicationFailure.newBuilder()
          .setMessage("payment failed: insufficient funds")
          .setType("PaymentError")
          .setNonRetryable(true)
          .build();
    }
    return gateway.charge(order);
  }
  // end slide

  // Every card is empty here, so chargePayment always takes the error path.
  private boolean cardHasFunds(Order order) {
    return false;
  }
}
