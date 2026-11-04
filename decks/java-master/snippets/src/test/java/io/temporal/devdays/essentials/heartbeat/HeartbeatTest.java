package io.temporal.devdays.essentials.heartbeat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.CanceledFailure;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 16-heartbeating.md: "Heartbeats in Java" and "Heartbeating and cancellation".
 *
 * <p>Both tests run in real time: they depend on Heartbeat Timeouts and on the Worker's Heartbeat
 * throttling, which are measured on the wall clock.
 */
class HeartbeatTest {
  private static final String TASK_QUEUE = "heartbeat";

  private TestWorkflowEnvironment env;
  private ShipmentActivitiesImpl shipments;
  private ProcessActivitiesImpl processing;

  @BeforeEach
  void setUp() {
    env =
        TestWorkflowEnvironment.newInstance(
            TestEnvironmentOptions.newBuilder().setUseTimeskipping(false).build());
    Worker worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(
        ShipmentWorkflowImpl.class, ProcessWorkflowImpl.class);
    shipments = new ShipmentActivitiesImpl();
    processing = new ProcessActivitiesImpl();
    worker.registerActivitiesImplementations(shipments, processing);
    env.start();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private WorkflowOptions options(String id) {
    return WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).setWorkflowId(id).build();
  }

  @Test
  void aRetryResumesFromTheLastHeartbeatsDetails() {
    ShipmentWorkflow workflow =
        env.getWorkflowClient().newWorkflowStub(ShipmentWorkflow.class, options("ship-1"));

    ShipmentResult result = workflow.run(new ShipmentInput("s-1", 5));

    assertEquals(5, result.itemsPacked(), "ShipmentActivitiesImpl.packShipment should pack all 5");
    assertEquals(
        2,
        result.attempt(),
        "ShipmentActivitiesImpl.packShipment: attempt 1 dies on item 2, so attempt 2 finishes");
    // Attempt 1 dies on item 2. Attempt 2 starts at item 2, not item 0.
    assertEquals(
        List.of(0, 1, 2, 2, 3, 4),
        shipments.packed,
        "ShipmentActivitiesImpl.packShipment: the retry should start from getHeartbeatDetails,"
            + " the last count heartbeat() recorded, not from item 0");
  }

  @Test
  void cancellationArrivesAtAHeartbeatAndTheActivityCleansUp() throws InterruptedException {
    ProcessWorkflow workflow =
        env.getWorkflowClient().newWorkflowStub(ProcessWorkflow.class, options("process-1"));
    List<String> items = IntStream.range(0, 100).mapToObj(i -> "item-" + i).toList();
    WorkflowClient.start(workflow::run, items);

    Thread.sleep(1000);
    WorkflowStub stub = WorkflowStub.fromTyped(workflow);
    stub.cancel();

    WorkflowFailedException e =
        assertThrows(
            WorkflowFailedException.class,
            () -> stub.getResult(Void.class),
            "ProcessWorkflowImpl.run should end as cancelled");
    assertInstanceOf(CanceledFailure.class, e.getCause());
    assertEquals(
        List.of(true),
        processing.locksReleased,
        "ProcessActivitiesImpl.processItems: heartbeat() should throw ActivityCanceledException,"
            + " and the catch block should release the locks exactly once");
    assertTrue(
        processing.processed.size() < items.size(),
        "ProcessActivitiesImpl.processItems should stop part way, at the Heartbeat after the"
            + " cancellation, not run to the end");
  }
}
