package io.temporal.devdays.shipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestActivityEnvironment;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * The Track's Check (ADR 0002), one test per part of TODO 1, in the order a
 * learner does them, so the first part still missing is the one reported. A
 * failing assertion's message is exactly what a learner sees when Check fails,
 * so every message names the file, the method and the TODO.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShipmentHeartbeatTest {

    /** What the real packShipment returned, and every value it Heartbeated. */
    private record Packed(ShipmentResult result, List<Integer> beats) {}

    /**
     * Runs the real packShipment, as if the last attempt Heartbeated lastHeartbeat
     * (null: none). An exception fails the test with a message that names the TODOs.
     */
    private static Packed pack(int itemCount, Integer lastHeartbeat) {
        try {
            return run(itemCount, lastHeartbeat);
        } catch (RuntimeException e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String thrown = root instanceof ApplicationFailure failure
                    ? failure.getType() + ": " + failure.getOriginalMessage()
                    : root.toString();
            return fail("packShipment threw " + thrown + " while packing " + itemCount + " items"
                    + (lastHeartbeat == null ? " on a first attempt" : " on a retry")
                    + ". Check TODO 1b (context.heartbeat(packed)) and TODO 1c (fall back to 0, e.g."
                    + " .orElse(0)) in ShipmentActivitiesImpl.java, packShipment.");
        }
    }

    private static Packed run(int itemCount, Integer lastHeartbeat) {
        TestActivityEnvironment env = TestActivityEnvironment.newInstance();
        try {
            env.registerActivitiesImplementations(new ShipmentActivitiesImpl());
            List<Integer> beats = new ArrayList<>();
            env.setActivityHeartbeatListener(Integer.class, beats::add);
            if (lastHeartbeat != null) {
                env.setHeartbeatDetails(lastHeartbeat);
            }
            // The SDK throttles Heartbeats to 0.8 of the Heartbeat Timeout. A short
            // timeout here lets every one through, one per 0.3-second item.
            ShipmentActivities activities = env.newActivityStub(
                    ShipmentActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(1))
                            .setHeartbeatTimeout(Duration.ofMillis(100))
                            .build());
            ShipmentResult result = activities.packShipment(new ShipmentInput("test", itemCount));
            return new Packed(result, beats);
        } finally {
            env.close();
        }
    }

    // --- TODO 1a: detection -------------------------------------------------------

    /**
     * Attempt 1 goes silent and never reports back, the way a crashed Worker does.
     * It sends no Heartbeat: a Heartbeat Timeout counts from the attempt's start.
     */
    static class WorkerDiesOnAttempt1 implements ShipmentActivities {
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public ShipmentResult packShipment(ShipmentInput input) {
            ActivityExecutionContext context = Activity.getExecutionContext();
            int attempt = context.getInfo().getAttempt();
            if (attempt == 1) {
                try {
                    release.await(1, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // Like a dead Worker, never report back.
                context.doNotCompleteOnReturn();
                return null;
            }
            return new ShipmentResult(input.shipmentId(), input.itemCount(), attempt);
        }
    }

    @Test
    @Order(1)
    @Tag("challenge1")
    void todo1aSilentAttemptIsRetriedQuickly() {
        TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance();
        WorkerDiesOnAttempt1 diesOnAttempt1 = new WorkerDiesOnAttempt1();
        ShipmentResult result = null;
        try {
            Worker worker = env.newWorker(Shared.TASK_QUEUE);
            worker.registerWorkflowImplementationTypes(ShipmentWorkflowImpl.class);
            worker.registerActivitiesImplementations(diesOnAttempt1);
            env.start();
            ShipmentWorkflow workflow = env.getWorkflowClient().newWorkflowStub(
                    ShipmentWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(Shared.TASK_QUEUE)
                            .setWorkflowId("test-" + UUID.randomUUID())
                            // Below the 20-second start-to-close timeout, well above a 2-second Heartbeat Timeout.
                            .setWorkflowExecutionTimeout(Duration.ofSeconds(8))
                            .build());
            result = workflow.run(new ShipmentInput("test", 10));
        } catch (WorkflowFailedException e) {
            result = null;
        } finally {
            diesOnAttempt1.release.countDown();
            env.close();
        }
        assertTrue(result != null && result.finishedOnAttempt() == 2,
                "A packing attempt went silent and Temporal waited out the whole 20-second start-to-close"
                        + " timeout before retrying. TODO 1a in ShipmentWorkflowImpl.java: add"
                        + " .setHeartbeatTimeout(Duration.ofSeconds(2)) to the ActivityOptions.");
    }

    // --- TODO 1b: send the Heartbeat ------------------------------------------------

    @Test
    @Order(2)
    @Tag("challenge1")
    void todo1bEveryItemIsHeartbeated() {
        List<Integer> beats = pack(3, null).beats();
        assertEquals(List.of(1, 2, 3), beats,
                "Packing 3 items Heartbeated " + (beats.isEmpty() ? "nothing" : beats.toString())
                        + ". TODO 1b in ShipmentActivitiesImpl.java, packShipment: call"
                        + " context.heartbeat(packed) after each item.");
    }

    // --- TODO 1c: read it back ------------------------------------------------------

    @Test
    @Order(3)
    @Tag("challenge1")
    void todo1cRetryResumesFromTheLastHeartbeat() {
        Packed run = pack(18, 16);
        int startedAt = run.beats().isEmpty() ? 0 : run.beats().get(0) - 1;
        assertEquals(16, startedAt,
                "The last attempt Heartbeated 16 packed items, and the retry started again from item "
                        + startedAt + ". TODO 1c in ShipmentActivitiesImpl.java, packShipment: start from"
                        + " context.getHeartbeatDetails(Integer.class) when there is one.");
        assertEquals(18, run.result().packed(),
                "Expected all 18 items packed and got " + run.result().packed()
                        + ". Check TODO 1c in ShipmentActivitiesImpl.java, packShipment.");
    }

    @Test
    @Order(4)
    @Tag("challenge1")
    void todo1cFirstAttemptStartsAtZero() {
        Packed run = pack(3, null);
        assertTrue(run.result().packed() == 3 && run.beats().equals(List.of(1, 2, 3)),
                "A first attempt has no Heartbeat to resume from and must start at item 0."
                        + " TODO 1c in ShipmentActivitiesImpl.java, packShipment: use 0 when"
                        + " getHeartbeatDetails(Integer.class) is empty, e.g. .orElse(0).");
    }
}
