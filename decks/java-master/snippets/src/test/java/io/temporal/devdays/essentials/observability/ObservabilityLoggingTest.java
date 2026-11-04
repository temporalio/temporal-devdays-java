package io.temporal.devdays.essentials.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.temporal.client.WorkflowOptions;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The logging slides: a Workflow's log lines are not repeated when it replays, and an Activity
 * logs once per attempt.
 *
 * <p>The MDC claims in the notes (WorkflowId, ActivityType, Attempt...) are not asserted here: the
 * harness logs through slf4j-simple, whose MDC adapter is a no-op.
 */
class ObservabilityLoggingTest {

  static final AtomicInteger workflowRuns = new AtomicInteger();

  /** Counts executions of the slide's Workflow method, without changing it. */
  public static class CountingOrderWorkflow extends OrderWorkflowImpl {
    @Override
    public String processOrder(String orderId) {
      workflowRuns.incrementAndGet();
      return super.processOrder(orderId);
    }
  }

  private static long count(String log, String message) {
    return log.lines().filter(l -> l.contains(message)).count();
  }

  @Test
  void workflowLinesAreNotRepeatedOnReplayAndTheActivityLogsOnce() throws Exception {
    String taskQueue = "observability-logging";
    String workflowId = UUID.randomUUID().toString();
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance();
    try {
      Worker worker = env.newWorker(taskQueue);
      worker.registerWorkflowImplementationTypes(CountingOrderWorkflow.class);
      worker.registerActivitiesImplementations(new OrderActivitiesImpl());
      env.start();

      String receipt =
          env.getWorkflowClient()
              .newWorkflowStub(
                  OrderWorkflow.class,
                  WorkflowOptions.newBuilder()
                      .setTaskQueue(taskQueue)
                      .setWorkflowId(workflowId)
                      .build())
              .processOrder("A-1");
      assertEquals("receipt-A-1", receipt);
      WorkflowExecutionHistory history = env.getWorkflowClient().fetchHistory(workflowId);

      // Replay the whole history: the Workflow method runs again from the top.
      int runsBefore = workflowRuns.get();
      WorkflowReplayer.replayWorkflowExecution(history, CountingOrderWorkflow.class);
      assertEquals(
          runsBefore + 1,
          workflowRuns.get(),
          "the replay should execute OrderWorkflowImpl.processOrder again");
    } finally {
      env.close();
      System.setErr(originalErr);
    }

    String log = captured.toString(StandardCharsets.UTF_8);
    assertEquals(
        1,
        count(log, "Order received: A-1"),
        "OrderWorkflowImpl (Logging from a Workflow): Workflow.getLogger must drop log calls made"
            + " during replay, so the line appears once. Log was:\n"
            + log);
    assertEquals(
        1,
        count(log, "Order charged: A-1 receipt-A-1"),
        "OrderWorkflowImpl's second line must also appear once despite the replay");
    assertEquals(
        1,
        count(log, "Charging card: A-1"),
        "OrderActivitiesImpl (Logging from an Activity) should log once: Activities are not"
            + " replayed");
  }
}
