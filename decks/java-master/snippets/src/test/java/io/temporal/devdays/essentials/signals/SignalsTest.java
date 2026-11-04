package io.temporal.devdays.essentials.signals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.history.v1.WorkflowExecutionSignaledEventAttributes;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the Workflow from WireTransferWorkflow.java and WireTransferWorkflowImpl.java (09-signals.md). */
class SignalsTest {
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

  private void startWorker(Class<?> impl) {
    Worker worker = env.newWorker("wires");
    worker.registerWorkflowImplementationTypes(impl);
    env.start();
  }

  private WireTransferWorkflow stub(String taskQueue, String workflowId) {
    return client.newWorkflowStub(
        WireTransferWorkflow.class,
        WorkflowOptions.newBuilder().setTaskQueue(taskQueue).setWorkflowId(workflowId).build());
  }

  @Test
  void aSignalUnblocksTheWaitingWorkflow() {
    startWorker(WireTransferWorkflowImpl.class);
    WireTransferWorkflow wire = stub("wires", "wire-1");
    WorkflowClient.start(wire::transfer, new WireTransfer("wire-1"));
    wire.approveWire("alice");
    assertEquals(
        "wire-1 approved by alice",
        WorkflowStub.fromTyped(wire).getResult(String.class),
        "WireTransferWorkflowImpl.java: approveWire should set approved, and Workflow.await"
            + " should then let transfer return");
  }

  @Test
  void theSignalAndItsArgumentAreRecordedInEventHistory() {
    startWorker(WireTransferWorkflowImpl.class);
    WireTransferWorkflow wire = stub("wires", "wire-2");
    WorkflowClient.start(wire::transfer, new WireTransfer("wire-2"));
    wire.approveWire("alice");
    WorkflowStub.fromTyped(wire).getResult(String.class);

    WorkflowExecutionSignaledEventAttributes signaled =
        client.fetchHistory("wire-2").getEvents().stream()
            .filter(HistoryEvent::hasWorkflowExecutionSignaledEventAttributes)
            .findFirst()
            .orElseThrow()
            .getWorkflowExecutionSignaledEventAttributes();
    assertEquals(
        "approveWire",
        signaled.getSignalName(),
        "09-signals.md: the Signal name is the @SignalMethod's method name");
    assertEquals(
        "\"alice\"",
        signaled.getInput().getPayloads(0).getData().toStringUtf8(),
        "09-signals.md: the Signal's argument is recorded in WorkflowExecutionSignaled");
  }

  @Test
  void aSignalIsAcceptedWhileNoWorkerIsRunningAndHandledOnceOneStarts() {
    env.start();
    WireTransferWorkflow wire = stub("late-wires", "wire-3");
    WorkflowClient.start(wire::transfer, new WireTransfer("wire-3"));
    // No Worker polls this Task Queue yet; the Server records the Signal anyway.
    wire.approveWire("carol");

    WorkerFactory factory = WorkerFactory.newInstance(client);
    factory.newWorker("late-wires").registerWorkflowImplementationTypes(WireTransferWorkflowImpl.class);
    factory.start();
    try {
      assertEquals(
          "wire-3 approved by carol",
          WorkflowStub.fromTyped(wire).getResult(String.class),
          "09-signals.md: a Signal sent before any Worker runs should wait in history and be"
              + " handled when a Worker picks the Workflow up");
    } finally {
      factory.shutdownNow();
    }
  }

  @Test
  void aSignalHandlerMayBlock() {
    startWorker(AsyncWireTransferWorkflowImpl.class);
    WireTransferWorkflow wire = stub("wires", "wire-4");
    WorkflowClient.start(wire::transfer, new WireTransfer("wire-4"));
    wire.approveWire("dave");
    assertEquals(
        "wire-4 approved by dave",
        WorkflowStub.fromTyped(wire).getResult(String.class),
        "AsyncWireTransferWorkflowImpl.java: a Signal handler may sleep, and the Workflow waits"
            + " on Workflow.isEveryHandlerFinished() before it returns");
  }
}
