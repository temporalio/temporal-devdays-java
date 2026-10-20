package io.temporal.devdays.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkflowImplementationOptions;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * One group of tests per Challenge, tagged challengeN (ADR 0002). These tests are
 * the Track's Checks: a failing assertion's message is exactly what a learner
 * sees when Check fails, so every message names the file, the method and the
 * TODO still to do.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderFulfillmentWorkflowTest {
    private static final String WORKFLOW = "OrderFulfillmentWorkflowImpl.java";
    private static final String ACTIVITIES = "OrderActivitiesImpl.java";

    private TestWorkflowEnvironment env;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private static io.temporal.devdays.saga.Order newOrder() {
        return io.temporal.devdays.saga.Order.widget("T-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private static List<String> expected(io.temporal.devdays.saga.Order order, String... steps) {
        return java.util.Arrays.stream(steps).map(s -> s + ":" + order.orderId()).toList();
    }

    /** Whether the order failed, and the ledger entries it left. */
    private record Outcome(boolean failed, List<String> entries) {}

    /** Runs one order on a fresh Worker, with the given Activity implementation. */
    private Outcome fulfil(io.temporal.devdays.saga.Order order, OrderActivities activities) {
        String taskQueue = UUID.randomUUID().toString();
        Worker worker = env.newWorker(taskQueue);
        // Fail the Workflow on any exception instead of retrying the Workflow Task,
        // so a half-finished TODO fails the test rather than hanging it.
        worker.registerWorkflowImplementationTypes(
                WorkflowImplementationOptions.newBuilder()
                        .setFailWorkflowExceptionTypes(Throwable.class)
                        .build(),
                OrderFulfillmentWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        env.start();

        OrderFulfillmentWorkflow workflow = env.getWorkflowClient().newWorkflowStub(
                OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(taskQueue)
                        .setWorkflowId("test-" + order.orderId())
                        .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                        .build());
        boolean failed;
        try {
            workflow.fulfill(order);
            failed = false;
        } catch (WorkflowFailedException e) {
            failed = true;
        }
        return new Outcome(failed, Ledger.entries(order.orderId()));
    }

    // --- Challenge 2: TODO 1, unwind ------------------------------------------------

    @Test
    @Order(1)
    @Tag("challenge2")
    void cleanOrderCompletes() {
        var order = newOrder();
        Outcome out = fulfil(order, new OrderActivitiesImpl());
        assertFalse(out.failed(),
                "A clean order failed. Something outside the TODOs changed: compare "
                        + WORKFLOW + " with the one in solution/.");
        assertEquals(expected(order, "reserve", "charge", "dispatch"), out.entries(),
                "A clean order should reserve, charge and dispatch, and the ledger shows " + out.entries()
                        + ". Compare " + WORKFLOW + " with the one in solution/.");
    }

    /**
     * A refund that takes a moment. Run one undo at a time, it still lands before
     * the release; run both at once, the release overtakes it.
     */
    static class SlowRefund extends OrderActivitiesImpl {
        @Override
        public void refundPaymentIfCharged(io.temporal.devdays.saga.Order order) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            super.refundPaymentIfCharged(order);
        }
    }

    @Test
    @Order(2)
    @Tag("challenge2")
    void failedCourierIsUndoneNewestFirst() {
        var order = newOrder().withFailDispatch();
        Outcome out = fulfil(order, new SlowRefund());
        assertTrue(out.failed(),
                "The courier failed but the Workflow completed. Keep the `throw e;` at the end of the catch block in "
                        + WORKFLOW + " (fulfill), so the order ends Failed.");
        assertTrue(out.entries().contains("refund:" + order.orderId())
                        && out.entries().contains("release:" + order.orderId()),
                "The courier failed and nothing was undone: the ledger shows only " + out.entries()
                        + ". TODO 1 in " + WORKFLOW + " (unwind): call saga.compensate().");
        assertNotEquals(expected(order, "reserve", "charge", "release", "refund"), out.entries(),
                "The release ran before the refund. TODO 1 in " + WORKFLOW
                        + " (newSaga): keep the Saga sequential (no setParallelCompensation), so it runs the undos one at a time, newest first.");
        assertEquals(expected(order, "reserve", "charge", "refund", "release"), out.entries(),
                "Expected reserve, charge, refund, release, and the ledger shows " + out.entries()
                        + ". Check TODO 1 in " + WORKFLOW + " (newSaga, unwind): keep the Saga sequential.");
    }

    /** The refund service is down: the refund undo always fails. */
    static class RefundServiceDown extends OrderActivitiesImpl {
        @Override
        public void refundPaymentIfCharged(io.temporal.devdays.saga.Order order) {
            throw ApplicationFailure.newNonRetryableFailure("refund service down", "RefundUnavailable");
        }
    }

    @Test
    @Order(3)
    @Tag("challenge2")
    void failingUndoDoesNotStrandTheRest() {
        var order = newOrder().withFailDispatch();
        Outcome out = fulfil(order, new RefundServiceDown());
        assertTrue(out.failed(),
                "The courier failed but the Workflow completed. Keep the `throw e;` at the end of the catch block in "
                        + WORKFLOW + " (fulfill).");
        assertTrue(out.entries().contains("release:" + order.orderId()),
                "The refund undo failed and the release never ran. TODO 1 in " + WORKFLOW
                        + " (newSaga): build the Saga with setContinueWithError(true), so one failing undo does not stop the rest.");
    }

    // --- Challenge 3: TODO 2a and 2b, register before the step ----------------------

    @Test
    @Order(4)
    @Tag("challenge3")
    void chargeThatLandsThenFailsIsRefunded() {
        var order = newOrder().withChargeFailsAfterEffect();
        Outcome out = fulfil(order, new OrderActivitiesImpl());
        assertTrue(out.failed(),
                "The charge failed but the Workflow completed. Keep the `throw e;` at the end of the catch block in "
                        + WORKFLOW + " (fulfill).");
        assertTrue(out.entries().contains("refund:" + order.orderId()),
                "The charge landed, then reported failure, and was never refunded. TODO 2b in " + WORKFLOW
                        + " (fulfill): register the refund with saga.addCompensation BEFORE chargePayment runs.");
        assertEquals(expected(order, "reserve", "charge", "refund", "release"), out.entries(),
                "Expected reserve, charge, refund, release, and the ledger shows " + out.entries()
                        + ". Check TODO 2b in " + WORKFLOW + " (fulfill).");
    }

    /** The reservation lands, then the inventory service times out anyway. */
    static class ReserveThenTimeOut extends OrderActivitiesImpl {
        @Override
        public String reserveInventory(io.temporal.devdays.saga.Order order) {
            Ledger.record("reserve:" + order.orderId());
            throw ApplicationFailure.newNonRetryableFailure(
                    "inventory service timed out after reserving", "InventoryTimeout");
        }
    }

    @Test
    @Order(5)
    @Tag("challenge3")
    void reservationThatLandsThenFailsIsReleased() {
        var order = newOrder();
        Outcome out = fulfil(order, new ReserveThenTimeOut());
        assertTrue(out.failed(),
                "The reservation failed but the Workflow completed. Keep the `throw e;` at the end of the catch block in "
                        + WORKFLOW + " (fulfill).");
        assertEquals(expected(order, "reserve", "release"), out.entries(),
                "The reservation landed, then reported failure, and the stock was never released. TODO 2a in "
                        + WORKFLOW + " (fulfill): register the release with saga.addCompensation BEFORE reserveInventory runs.");
    }

    // --- Challenge 4: TODO 3a and 3b, safe to repeat --------------------------------

    @Test
    @Order(6)
    @Tag("challenge4")
    void releaseWithNothingReservedDoesNothing() {
        var order = newOrder();
        new OrderActivitiesImpl().releaseInventoryIfReserved(order);
        assertEquals(List.of(), Ledger.entries(order.orderId()),
                "releaseInventoryIfReserved released stock that was never reserved. TODO 3a in " + ACTIVITIES
                        + ": return early when Ledger.isReserved(order.orderId()) is false.");
    }

    @Test
    @Order(7)
    @Tag("challenge4")
    void releaseTwiceReleasesOnce() {
        var order = newOrder();
        var activities = new OrderActivitiesImpl();
        activities.reserveInventory(order);
        activities.releaseInventoryIfReserved(order);
        activities.releaseInventoryIfReserved(order);
        assertEquals(expected(order, "reserve", "release"), Ledger.entries(order.orderId()),
                "Running releaseInventoryIfReserved twice released the stock twice. TODO 3a in " + ACTIVITIES
                        + ": return early when nothing is reserved.");
    }

    @Test
    @Order(8)
    @Tag("challenge4")
    void refundWithNothingChargedDoesNothing() {
        var order = newOrder();
        new OrderActivitiesImpl().refundPaymentIfCharged(order);
        assertEquals(List.of(), Ledger.entries(order.orderId()),
                "refundPaymentIfCharged refunded a charge that never happened. TODO 3b in " + ACTIVITIES
                        + ": return early when Ledger.isCharged(order.orderId()) is false.");
    }

    @Test
    @Order(9)
    @Tag("challenge4")
    void refundTwiceRefundsOnce() {
        var order = newOrder();
        var activities = new OrderActivitiesImpl();
        activities.chargePayment(order);
        activities.refundPaymentIfCharged(order);
        activities.refundPaymentIfCharged(order);
        assertEquals(expected(order, "charge", "refund"), Ledger.entries(order.orderId()),
                "Running refundPaymentIfCharged twice refunded the customer twice. TODO 3b in " + ACTIVITIES
                        + ": return early when nothing is charged.");
    }

    @Test
    @Order(10)
    @Tag("challenge4")
    void failedReservationReleasesNothing() {
        var order = newOrder().withFailReserve();
        Outcome out = fulfil(order, new OrderActivitiesImpl());
        assertTrue(out.failed(),
                "The reservation failed but the Workflow completed. Keep the `throw e;` at the end of the catch block in "
                        + WORKFLOW + " (fulfill).");
        assertEquals(List.of(), out.entries(),
                "Inventory never reserved anything, and the ledger shows " + out.entries() + ". TODO 3a in "
                        + ACTIVITIES + ": return early when nothing is reserved.");
    }
}
