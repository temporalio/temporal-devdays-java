package io.temporal.devdays.saga;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Order fulfillment as a Saga: three steps forward, two undos backward.
 *
 * The SDK's {@link Saga} class is the compensation stack: each
 * {@code addCompensation} pushes one undo, and {@code compensate()} runs them.
 */
public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {
    private static final Logger log = Workflow.getLogger(OrderFulfillmentWorkflowImpl.class);

    // Every Activity, undos included, gets two quick attempts. A compensation is
    // an ordinary Activity with an ordinary retry policy, so it can run more than once.
    private final OrderActivities activities = Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofMillis(100))
                            .setMaximumAttempts(2)
                            .build())
                    .build());

    @Override
    public OrderResult fulfill(Order order) {
        Saga saga = newSaga();

        try {
            // TODO 2a start: register the undo BEFORE the step, so swap these two statements
            saga.addCompensation(activities::releaseInventoryIfReserved, order);
            activities.reserveInventory(order);
            // TODO 2a end

            // TODO 2b start: same swap for the charge
            saga.addCompensation(activities::refundPaymentIfCharged, order);
            activities.chargePayment(order);
            // TODO 2b end

            String trackingNumber = activities.dispatchCourier(order);
            return new OrderResult(order.orderId(), trackingNumber);

        } catch (ActivityFailure e) {
            log.warn("fulfillment failed for {}, unwinding: {}", order.orderId(), Shared.rootCause(e));
            unwind(saga);
            // Compensating is not succeeding. Rethrow so the Workflow ends Failed and
            // the caller learns the order did not go through.
            throw e;
        }
    }

    // TODO 1 start: run the undos newest first, and keep going when one fails
    private static Saga newSaga() {
        // Sequential is the default: one undo at a time, newest first.
        // continueWithError: a failing undo does not stop the ones after it.
        return new Saga(new Saga.Options.Builder().setContinueWithError(true).build());
    }

    private static void unwind(Saga saga) {
        saga.compensate();
    }
    // TODO 1 end
}
