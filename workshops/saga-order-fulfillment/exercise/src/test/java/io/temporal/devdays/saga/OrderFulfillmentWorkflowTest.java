package io.temporal.devdays.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
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

    // --- Challenge 1: no TODO; the Client's summary of the Event History --------------

    /** The summary the starter prints for one order, read from its Event History. */
    private HistorySummary summarize(io.temporal.devdays.saga.Order order) {
        fulfil(order, new OrderActivitiesImpl());
        return HistorySummary.of(env.getWorkflowClient().fetchHistory("test-" + order.orderId()), order);
    }

    private static final String SUMMARY = "HistorySummary.java";

    @Test
    @Order(1)
    @Tag("challenge1")
    void summaryOfCleanOrder() {
        HistorySummary s = summarize(newOrder());
        assertEquals(List.of("ReserveInventory", "ChargePayment", "DispatchCourier"), s.stepsCompleted(),
                "The Client's summary misread a clean order's Event History. Compare " + SUMMARY
                        + " with the one in solution/.");
        assertEquals(List.of(), s.undosCompleted(), "A clean order ran no undo. Compare " + SUMMARY
                + " with the one in solution/.");
        assertEquals(List.of(), s.undosOwed(), "A clean order owes no undo. Compare " + SUMMARY
                + " with the one in solution/.");
    }

    /** Every undo the order owed is either completed or still owed, never both, never neither. */
    private static void assertAccounted(HistorySummary s, List<String> owed) {
        List<String> all = new java.util.ArrayList<>(s.undosCompleted().stream().filter(owed::contains).toList());
        all.addAll(s.undosOwed());
        assertEquals(new java.util.TreeSet<>(owed), new java.util.TreeSet<>(all),
                "The Client's summary lost track of an undo: completed " + s.undosCompleted() + ", still owed "
                        + s.undosOwed() + ". Compare " + SUMMARY + " with the one in solution/.");
        assertEquals(all.size(), new java.util.HashSet<>(all).size(),
                "The Client's summary lists an undo as both completed and still owed. Compare " + SUMMARY
                        + " with the one in solution/.");
    }

    @Test
    @Order(2)
    @Tag("challenge1")
    void summaryOfFailedCourier() {
        HistorySummary s = summarize(newOrder().withFailDispatch());
        assertEquals(List.of("ReserveInventory", "ChargePayment"), s.stepsCompleted(),
                "The Client's summary misread the steps. Compare " + SUMMARY + " with the one in solution/.");
        assertEquals("DispatchCourier", s.stepFailed(),
                "The Client's summary misread the failed step. Compare " + SUMMARY + " with the one in solution/.");
        assertAccounted(s, List.of("RefundPaymentIfCharged", "ReleaseInventoryIfReserved"));
    }

    @Test
    @Order(3)
    @Tag("challenge1")
    void summaryOfChargeThatLandsThenFails() {
        HistorySummary s = summarize(newOrder().withChargeFailsAfterEffect());
        assertEquals("ChargePayment", s.stepFailed(),
                "The Client's summary misread the failed step. Compare " + SUMMARY + " with the one in solution/.");
        // The charge failed in the history but captured the money, so it owes a refund.
        assertAccounted(s, List.of("RefundPaymentIfCharged", "ReleaseInventoryIfReserved"));
    }

    // --- Challenge 2: TODO 1, unwind ------------------------------------------------

    @Test
    @Order(4)
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
    @Order(5)
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
    @Order(6)
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

    /** A courier booking that takes a while, so the order can be cancelled mid-dispatch. */
    static class SlowDispatch extends OrderActivitiesImpl {
        @Override
        public String dispatchCourier(io.temporal.devdays.saga.Order order) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return super.dispatchCourier(order);
        }
    }

    @Test
    @Order(7)
    @Tag("challenge2")
    void cancelledOrderStillUnwinds() throws InterruptedException {
        var order = newOrder();
        String taskQueue = UUID.randomUUID().toString();
        Worker worker = env.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(OrderFulfillmentWorkflowImpl.class);
        worker.registerActivitiesImplementations(new SlowDispatch());
        env.start();
        WorkflowStub stub = env.getWorkflowClient().newUntypedWorkflowStub("OrderFulfillmentWorkflow",
                WorkflowOptions.newBuilder().setTaskQueue(taskQueue).setWorkflowId("test-" + order.orderId()).build());
        stub.start(order);
        for (int i = 0; i < 100 && !Ledger.entries(order.orderId()).contains("charge:" + order.orderId()); i++) {
            Thread.sleep(50);
        }
        stub.cancel();
        try {
            stub.getResult(OrderResult.class);
        } catch (WorkflowFailedException expected) {
            // Cancelled: the order did not go through.
        }
        assertTrue(Ledger.entries(order.orderId()).containsAll(expected(order, "refund", "release")),
                "The order was cancelled mid-dispatch and nothing was undone: the ledger shows "
                        + Ledger.entries(order.orderId()) + ". In " + WORKFLOW
                        + " (fulfill), keep unwind(saga) inside Workflow.newDetachedCancellationScope(...).run(), so a cancelled Workflow still runs its undos.");
    }

    // --- Challenge 3: TODO 2a and 2b, register before the step ----------------------

    @Test
    @Order(8)
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
    @Order(9)
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
    @Order(10)
    @Tag("challenge4")
    void releaseWithNothingReservedDoesNothing() {
        var order = newOrder();
        new OrderActivitiesImpl().releaseInventoryIfReserved(order);
        assertEquals(List.of(), Ledger.entries(order.orderId()),
                "releaseInventoryIfReserved released stock that was never reserved. TODO 3a in " + ACTIVITIES
                        + ": return early when Ledger.isReserved(order.orderId()) is false.");
    }

    @Test
    @Order(11)
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
    @Order(12)
    @Tag("challenge4")
    void refundWithNothingChargedDoesNothing() {
        var order = newOrder();
        new OrderActivitiesImpl().refundPaymentIfCharged(order);
        assertEquals(List.of(), Ledger.entries(order.orderId()),
                "refundPaymentIfCharged refunded a charge that never happened. TODO 3b in " + ACTIVITIES
                        + ": return early when Ledger.isCharged(order.orderId()) is false.");
    }

    @Test
    @Order(13)
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
    @Order(14)
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
