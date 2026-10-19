package io.temporal.devdays.saga;

import io.temporal.failure.ApplicationFailure;

/**
 * Each undo is named "...IfX" because it must cope with running when X never
 * happened.
 */
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
        // The effect lands first, exactly as a real gateway captures the money
        // before it returns. Then the call reports failure.
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

    /** Undo for reserveInventory. Can run when nothing is reserved, and more than once. */
    @Override
    public void releaseInventoryIfReserved(Order order) {
        // TODO 3a start: return early when Ledger.isReserved(order.orderId()) is false
        if (!Ledger.isReserved(order.orderId())) {
            System.out.println("[compensate] nothing reserved for " + order.orderId() + ", no-op");
            return;
        }
        // TODO 3a end
        Ledger.record("release:" + order.orderId());
    }

    /** Undo for chargePayment. Same two reasons it can run with nothing to undo. */
    @Override
    public void refundPaymentIfCharged(Order order) {
        // TODO 3b start: return early when Ledger.isCharged(order.orderId()) is false
        if (!Ledger.isCharged(order.orderId())) {
            System.out.println("[compensate] nothing charged for " + order.orderId() + ", no-op");
            return;
        }
        // TODO 3b end
        Ledger.record("refund:" + order.orderId());
    }
}
