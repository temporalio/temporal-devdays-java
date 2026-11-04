package io.temporal.devdays.essentials.saga;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;

/**
 * Order fulfillment as a Saga, as in the Saga Workshop's solution
 * (workshops/saga-order-fulfillment). The Workshop splits the Saga's construction and the unwind
 * into newSaga() and unwind() for its TODO 1; the slide shows them inline.
 */
public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {
  private static final Logger log = Workflow.getLogger(OrderFulfillmentWorkflowImpl.class);

  // As in the Workshop: a 10 second Start-To-Close Timeout and two attempts, undos included.
  private final OrderActivities activities =
      Workflow.newActivityStub(
          OrderActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofSeconds(10))
              .setRetryOptions(
                  RetryOptions.newBuilder()
                      .setInitialInterval(Duration.ofMillis(100))
                      .setMaximumAttempts(2)
                      .build())
              .build());

  // slide: 17-saga-03-implementation.md "heading: Implementation"
  @Override
  public OrderResult fulfill(Order order) {
    Saga saga =
        new Saga(new Saga.Options.Builder().setContinueWithError(true).build());

    try {
      saga.addCompensation(activities::releaseInventoryIfReserved, order);
      activities.reserveInventory(order);

      saga.addCompensation(activities::refundPaymentIfCharged, order);
      activities.chargePayment(order);

      String trackingNumber = activities.dispatchCourier(order);
      return new OrderResult(order.orderId(), trackingNumber);
    } catch (ActivityFailure e) {
      log.warn("fulfillment failed for {}, unwinding: {}",
          order.orderId(), Shared.rootCause(e));
      saga.compensate();
      throw e;
    }
  }
  // end slide
}
