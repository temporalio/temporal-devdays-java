package io.temporal.devdays.essentials.resumable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionDescription;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 17b-patterns-and-anti-patterns.md: "Resumable Activity: you're on call", the console in
 * components/ResumableTransfer.vue, and the demo after it.
 */
class ResumableTest {
  private TestWorkflowEnvironment env;
  private TransferActivitiesImpl activities;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    Worker worker = env.newWorker(ResumableDemo.TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
    activities = new TransferActivitiesImpl();
    worker.registerActivitiesImplementations(activities);
    env.start();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private static void until(java.util.function.BooleanSupplier check) throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      if (check.getAsBoolean()) return;
      Thread.sleep(100);
    }
    throw new AssertionError("condition never became true");
  }

  @Test
  void parksOnABadAccountIgnoresAMalformedFixAndResumesOnAValidOne() throws InterruptedException {
    WorkflowClient client = env.getWorkflowClient();
    TransferWorkflow transfer =
        client.newWorkflowStub(
            TransferWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(ResumableDemo.TASK_QUEUE)
                .setWorkflowId("transfer-4471")
                .build());
    WorkflowClient.start(transfer::transfer, new Transfer("10000001", "99999999", 500));

    until(() -> transfer.getStatus().equals("AWAITING_CORRECTION"));
    // Non-retryable: one attempt, not the Retry Policy's three.
    assertEquals(
        List.of("99999999"),
        activities.attempts,
        "TransferActivitiesImpl.executeTransfer: AccountNotFoundError is non-retryable, so one"
            + " attempt");

    // A Query adds nothing to history.
    long before = client.fetchHistory("transfer-4471").getEvents().size();
    transfer.getStatus();
    assertEquals(
        before,
        client.fetchHistory("transfer-4471").getEvents().size(),
        "TransferWorkflowImpl.getStatus: a Query should not add Events");

    // The handler drops a correction in the wrong format; the Workflow stays parked.
    transfer.retryWithCorrection("acct 1234");
    Thread.sleep(500);
    assertEquals(
        "AWAITING_CORRECTION",
        transfer.getStatus(),
        "TransferWorkflowImpl.retryWithCorrection should ignore an account that is not 8 to 12"
            + " digits");
    assertEquals(List.of("99999999"), activities.attempts, "no transfer after a malformed fix");

    transfer.retryWithCorrection("12345678");
    WorkflowStub stub = WorkflowStub.fromTyped(transfer);
    assertEquals(
        "Transfer of 500 to 12345678 completed",
        stub.getResult(String.class),
        "TransferWorkflowImpl.transfer should resume with the corrected account");
    assertEquals(List.of("99999999", "12345678"), activities.attempts);

    // Same Workflow Execution throughout: no restart, no new run.
    WorkflowExecutionDescription description = stub.describe();
    assertEquals(
        description.getFirstRunId(),
        description.getExecution().getRunId(),
        "TransferWorkflowImpl.transfer: one run from start to finish");

    // The names the console and the demo's CLI commands use.
    List<HistoryEvent> events = client.fetchHistory("transfer-4471").getEvents();
    assertEquals("TransferWorkflow", description.getWorkflowType());
    assertTrue(
        events.stream()
            .filter(HistoryEvent::hasActivityTaskScheduledEventAttributes)
            .allMatch(
                e ->
                    e.getActivityTaskScheduledEventAttributes()
                        .getActivityType()
                        .getName()
                        .equals("ExecuteTransfer")),
        "the Activity Type the console shows is ExecuteTransfer");
    assertEquals(
        2,
        events.stream().filter(HistoryEvent::hasWorkflowExecutionSignaledEventAttributes).count(),
        "both Signals, the malformed one included, are recorded in history");
  }
}
