package io.temporal.devdays.saga;

import io.temporal.activity.ActivityInterface;

/** The five Activities an order touches: three forward steps and two undos. */
@ActivityInterface
public interface OrderActivities {
    String reserveInventory(Order order);

    String chargePayment(Order order);

    String dispatchCourier(Order order);

    /** Undo for reserveInventory. */
    void releaseInventoryIfReserved(Order order);

    /** Undo for chargePayment. */
    void refundPaymentIfCharged(Order order);
}
