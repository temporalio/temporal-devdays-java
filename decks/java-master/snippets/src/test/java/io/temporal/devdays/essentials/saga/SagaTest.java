package io.temporal.devdays.essentials.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.devdays.essentials.deck.Slides;
import io.temporal.devdays.essentials.saga.TitleTransfer.TitleActivitiesImpl;
import io.temporal.devdays.essentials.saga.TitleTransfer.TitleOrder;
import io.temporal.devdays.essentials.saga.TitleTransfer.TitleTransferWorkflow;
import io.temporal.devdays.essentials.saga.TitleTransfer.TitleTransferWorkflowImpl;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the Saga section's snippets and checks what its slides and speaker notes claim. */
class SagaTest {
  private static final String TASK_QUEUE = "fulfillment-tasks";
  private static final String WORKFLOW = "OrderFulfillmentWorkflowImpl.java";

  private TestWorkflowEnvironment env;
  private WorkflowClient client;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private static String id() {
    return "ORD-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private OrderResult fulfill(Order order, OrderActivities activities) {
    Worker worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(OrderFulfillmentWorkflowImpl.class);
    worker.registerActivitiesImplementations(activities);
    env.start();
    return client
        .newWorkflowStub(
            OrderFulfillmentWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId("fulfillment-" + order.orderId())
                .build())
        .fulfill(order);
  }

  /** The type of the ApplicationFailure inside the ActivityFailure the Workflow re-threw. */
  private String failureType(Order order, OrderActivities activities) {
    WorkflowFailedException e =
        assertThrows(
            WorkflowFailedException.class,
            () -> fulfill(order, activities),
            WORKFLOW + ": the Workflow should end Failed. The slide's `throw e` re-throws after"
                + " compensating");
    ActivityFailure activity =
        assertInstanceOf(
            ActivityFailure.class,
            e.getCause(),
            WORKFLOW + ": the Workflow should fail with the original ActivityFailure, re-thrown");
    return assertInstanceOf(ApplicationFailure.class, activity.getCause()).getType();
  }

  @Test
  void theHappyPathRunsNoUndo() {
    Order o = Order.widget(id());
    OrderResult result = fulfill(o, new OrderActivitiesImpl());
    assertEquals(
        new OrderResult(o.orderId(), "TRK-" + o.orderId()),
        result,
        WORKFLOW + ": the happy path returns the tracking number");
    assertEquals(
        List.of("reserve:" + o.orderId(), "charge:" + o.orderId(), "dispatch:" + o.orderId()),
        Ledger.entries(o.orderId()),
        WORKFLOW + ": on the happy path the Saga is dropped and no undo runs");
  }

  @Test
  void aFailedDispatchUnwindsNewestFirstAndRethrows() {
    Order o = Order.widget(id()).withFailDispatch();
    assertEquals(
        "CourierUnavailable",
        failureType(o, new OrderActivitiesImpl()),
        WORKFLOW + ": the caller sees the courier's failure, not a compensation's");
    String k = ":" + o.orderId();
    assertEquals(
        List.of("reserve" + k, "charge" + k, "refund" + k, "release" + k),
        Ledger.entries(o.orderId()),
        WORKFLOW + ": saga.compensate() runs the undos newest first, refund then release");
  }

  @Test
  void aChargeThatLandsThenFailsIsRefundedBecauseTheUndoWasRegisteredFirst() {
    Order o = Order.widget(id()).withChargeFailsAfterEffect();
    assertEquals("PaymentGatewayTimeout", failureType(o, new OrderActivitiesImpl()));
    String k = ":" + o.orderId();
    assertEquals(
        List.of("reserve" + k, "charge" + k, "refund" + k, "release" + k),
        Ledger.entries(o.orderId()),
        WORKFLOW + ": addCompensation before chargePayment means a charge that lands and then"
            + " fails is still refunded");
  }

  @Test
  void anUndoWhoseStepNeverLandedIsANoOp() {
    Order o = Order.widget(id()).withFailReserve();
    assertEquals("InventoryUnavailable", failureType(o, new OrderActivitiesImpl()));
    assertEquals(
        List.of(),
        Ledger.entries(o.orderId()),
        "OrderActivitiesImpl.java releaseInventoryIfReserved: ran (it was registered first) and"
            + " found nothing to release, so it must record nothing");
  }

  @Test
  void oneFailingCompensationDoesNotStrandTheRest() {
    Order o = Order.widget(id()).withFailDispatch();
    OrderActivities broken =
        new OrderActivitiesImpl() {
          @Override
          public void refundPaymentIfCharged(Order order) {
            throw ApplicationFailure.newNonRetryableFailure("refund service down", "RefundDown");
          }
        };
    assertEquals(
        "CourierUnavailable",
        failureType(o, broken),
        WORKFLOW + ": the original failure, not the compensation's, is what the caller sees");
    String k = ":" + o.orderId();
    assertEquals(
        List.of("reserve" + k, "charge" + k, "release" + k),
        Ledger.entries(o.orderId()),
        WORKFLOW + ": setContinueWithError(true) means a failed refund does not stop the release");
  }

  @Test
  void theUnwindSlideShowsTheSameCodeAsTheWalkthrough() {
    assertEquals(
        Slides.slideCode("17-saga-03-implementation.md", "heading: Implementation"),
        Slides.slideCode("17-saga-03-implementation.md", "# Implementation: the unwind"),
        "17-saga-03-implementation.md: the unwind slide must show the walkthrough's code");
  }

  @Test
  void anUndoRegisteredAfterItsStepUsesTheForwardResult() {
    TitleTransfer.JOURNAL.clear();
    Worker worker = env.newWorker("titles");
    worker.registerWorkflowImplementationTypes(TitleTransferWorkflowImpl.class);
    worker.registerActivitiesImplementations(new TitleActivitiesImpl());
    env.start();
    TitleOrder o = new TitleOrder(id(), "transferTitle");
    TitleTransferWorkflow wf =
        client.newWorkflowStub(
            TitleTransferWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("titles").build());
    assertThrows(WorkflowFailedException.class, () -> wf.transfer(o));
    // The title transfer failed before its undo was registered, so only the refund runs, with
    // the confirmation the payment returned.
    assertEquals(
        List.of(
            "checkFraud", "processPayment", "transferTitle", "refundPayment:conf-" + o.orderId()),
        TitleTransfer.JOURNAL,
        "TitleTransfer.java: an undo registered after its step can use the step's result");
  }
}
