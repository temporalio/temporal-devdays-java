package io.temporal.devdays.essentials.saga;

import io.temporal.failure.ApplicationFailure;

/** Each undo is named "...IfX" because it must cope with running when X never happened. */
public class OrderActivitiesImpl implements OrderActivities {

  @Override
  public String reserveInventory(Order order) {
    if (order.failReserve()) {
      // Fails BEFORE recording, so nothing lands. The undo still runs.
      throw ApplicationFailure.newNonRetryableFailure(
          "inventory service unavailable", "InventoryUnavailable");
    }
    Ledger.record("reserve:" + order.orderId());
    return "reserved " + order.quantity() + " x " + order.sku();
  }

  @Override
  public String chargePayment(Order order) {
    // The effect lands first, as a real gateway captures the money before it returns.
    Ledger.record("charge:" + order.orderId());
    if (order.chargeFailsAfterEffect()) {
      throw ApplicationFailure.newNonRetryableFailure(
          "payment gateway timed out after capturing " + order.amountCents() + " cents",
          "PaymentGatewayTimeout");
    }
    return "charged " + order.amountCents() + " cents";
  }

  @Override
  public String dispatchCourier(Order order) {
    if (order.failDispatch()) {
      throw ApplicationFailure.newNonRetryableFailure(
          "no courier available for this route", "CourierUnavailable");
    }
    Ledger.record("dispatch:" + order.orderId());
    return "TRK-" + order.orderId();
  }

  // slide: 17-saga-06-practices.md "heading: An idempotent compensation"
  @Override
  public void releaseInventoryIfReserved(Order order) {
    if (!Ledger.isReserved(order.orderId())) {
      System.out.println("[compensate] nothing reserved for " + order.orderId() + ", no-op");
      return;
    }
    Ledger.record("release:" + order.orderId());
  }
  // end slide

  @Override
  public void refundPaymentIfCharged(Order order) {
    if (!Ledger.isCharged(order.orderId())) {
      System.out.println("[compensate] nothing charged for " + order.orderId() + ", no-op");
      return;
    }
    Ledger.record("refund:" + order.orderId());
  }
}
