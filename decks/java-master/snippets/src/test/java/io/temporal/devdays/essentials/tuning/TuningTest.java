package io.temporal.devdays.essentials.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.failure.v1.Failure;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.worker.WorkerOptions;
import io.temporal.worker.tuning.ResourceBasedControllerOptions;
import io.temporal.worker.tuning.ResourceBasedTuner;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The worker-tuning slides (26-worker-tuning.md). */
class TuningTest {

  @Test
  void theTablesDefaultsAreTheSdksDefaults() {
    WorkerOptions w = WorkerOptions.getDefaultInstance();
    String where = "26-worker-tuning.md table says ";
    assertEquals(200, w.getMaxConcurrentWorkflowTaskExecutionSize(), where + "200 Workflow Task slots");
    assertEquals(200, w.getMaxConcurrentActivityExecutionSize(), where + "200 Activity slots");
    assertEquals(200, w.getMaxConcurrentLocalActivityExecutionSize(), where + "200 Local Activity slots");
    assertEquals(5, w.getMaxConcurrentWorkflowTaskPollers(), where + "5 Workflow Task pollers");
    assertEquals(5, w.getMaxConcurrentActivityTaskPollers(), where + "5 Activity Task pollers");
    assertEquals(0.0, w.getMaxTaskQueueActivitiesPerSecond(), where + "unlimited (0) per Task Queue");
    assertEquals(0.0, w.getMaxWorkerActivitiesPerSecond(), where + "unlimited (0) per Worker");
    assertEquals(1000, w.getDefaultDeadlockDetectionTimeout(), "notes say the deadlock detector fires after 1 second");

    WorkerFactoryOptions f = WorkerFactoryOptions.newBuilder().validateAndBuildWithDefaults();
    assertEquals(600, f.getWorkflowCacheSize(), where + "a 600-Workflow sticky cache");
    assertEquals(600, f.getMaxWorkflowThreadCount(), where + "600 Workflow threads");

    // TuningOptions compiles every option the slides name; building them must not throw.
    assertNotNull(TuningOptions.fixedSlots());
    assertNotNull(TuningOptions.memory());
    assertNotNull(TuningOptions.composite());
  }

  @Test
  void aWorkerWithAResourceBasedTunerRunsWorkflowsAndActivities() {
    try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
      TunedWorker.create(env.getWorkerFactory());
      env.start();
      String result =
          env.getWorkflowClient()
              .newWorkflowStub(
                  SubmitWorkflow.class,
                  WorkflowOptions.newBuilder()
                      .setTaskQueue("order-task-queue")
                      .setWorkflowId(UUID.randomUUID().toString())
                      .build())
              .submit("A-1");
      assertEquals(
          "submitted A-1",
          result,
          "TunedWorker.java (Execution slot auto-tuning) should run Workflows and Activities");
    }
  }

  @Test
  void aTunerCannotBeCombinedWithFixedSlotCounts() {
    try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
      WorkerOptions both =
          WorkerOptions.newBuilder()
              .setMaxConcurrentWorkflowTaskExecutionSize(200)
              .setWorkerTuner(
                  ResourceBasedTuner.newBuilder()
                      .setControllerOptions(
                          ResourceBasedControllerOptions.newBuilder(0.8, 0.8).build())
                      .build())
              .build();
      IllegalStateException e =
          assertThrows(
              IllegalStateException.class,
              () -> env.newWorker("order-task-queue", both),
              "the notes claim newWorker throws when a tuner and a fixed slot count are both set");
      assertTrue(
          e.getMessage()
              .contains("maxConcurrentWorkflowTaskExecutionSize must not be set if workerTuner is set"),
          e.getMessage());
    }
  }

  /** Every message in a failure's cause chain: the SDK wraps the deadlock in a task failure. */
  private static String causeChain(Failure failure) {
    StringBuilder messages = new StringBuilder(failure.getMessage());
    for (Failure f = failure; f.hasCause(); f = f.getCause()) {
      messages.append(" <- ").append(f.getCause().getMessage());
    }
    return messages.toString();
  }

  @Test
  void workflowCodeThatNeverYieldsFailsItsWorkflowTaskAndTheWorkflowStaysOpen() throws Exception {
    try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
      Worker worker = env.newWorker("busy");
      worker.registerWorkflowImplementationTypes(BusyWorkflow.Impl.class);
      env.start();
      WorkflowClient client = env.getWorkflowClient();
      BusyWorkflow busy =
          client.newWorkflowStub(
              BusyWorkflow.class,
              WorkflowOptions.newBuilder()
                  .setTaskQueue("busy")
                  .setWorkflowId(UUID.randomUUID().toString())
                  .build());
      String id = WorkflowClient.start(busy::spin, 1_500L).getWorkflowId();

      Optional<String> failure = Optional.empty();
      for (int i = 0; i < 50 && failure.isEmpty(); i++) {
        Thread.sleep(200);
        failure =
            client.fetchHistory(id).getEvents().stream()
                .filter(ev -> ev.getEventType() == EventType.EVENT_TYPE_WORKFLOW_TASK_FAILED)
                .map(HistoryEvent::getWorkflowTaskFailedEventAttributes)
                .map(a -> causeChain(a.getFailure()))
                .findFirst();
      }
      assertTrue(
          failure.isPresent() && failure.get().contains("Potential deadlock detected"),
          "BusyWorkflow holds its thread for 1.5s: the notes claim the deadlock detector fails"
              + " the Workflow Task. Got: " + failure);
      WorkflowStub untyped = client.newUntypedWorkflowStub(id);
      assertEquals(
          WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING,
          untyped.describe().getStatus(),
          "the Workflow must stay open, retrying its Workflow Task");
      untyped.terminate("test done");
    }
  }
}
