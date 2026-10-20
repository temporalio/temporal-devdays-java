package io.temporal.devdays.saga;

/**
 * One order, and the switches that make fulfillment fail on purpose.
 *
 * @param failReserve inventory reservation fails BEFORE it records anything: the
 *     service was down, so the effect never landed. Its undo was already
 *     registered (that is the point of registering first), so it runs anyway and
 *     has to cope with nothing to undo.
 * @param failDispatch courier dispatch fails, so the undos have to run. The
 *     ordinary failure: two steps landed, the third did not.
 * @param chargeFailsAfterEffect the charge lands and then reports failure anyway,
 *     which is what a gateway timeout after a successful capture looks like. The
 *     money moved but the Workflow was told it did not, so an undo registered
 *     AFTER the step is never registered and the charge is stranded.
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
