package io.temporal.devdays.essentials.saga;

/**
 * One order, and the switches that make fulfillment fail on purpose. As in the Saga Workshop
 * (workshops/saga-order-fulfillment).
 *
 * @param failReserve inventory reservation fails before it records anything
 * @param failDispatch courier dispatch fails, so the undos have to run
 * @param chargeFailsAfterEffect the charge lands and then reports failure anyway
 */
public record Order(
    String orderId,
    String sku,
    int quantity,
    long amountCents,
    boolean failReserve,
    boolean failDispatch,
    boolean chargeFailsAfterEffect) {

  /** One widget for 49.99, with no failure switched on. */
  public static Order widget(String orderId) {
    return new Order(orderId, "widget", 1, 4999, false, false, false);
  }

  public Order withFailReserve() {
    return new Order(orderId, sku, quantity, amountCents, true, failDispatch, chargeFailsAfterEffect);
  }

  public Order withFailDispatch() {
    return new Order(orderId, sku, quantity, amountCents, failReserve, true, chargeFailsAfterEffect);
  }

  public Order withChargeFailsAfterEffect() {
    return new Order(orderId, sku, quantity, amountCents, failReserve, failDispatch, true);
  }
}
