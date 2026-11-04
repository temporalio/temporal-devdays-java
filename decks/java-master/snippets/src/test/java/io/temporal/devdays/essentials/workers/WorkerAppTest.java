package io.temporal.devdays.essentials.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Builds the Worker from WorkerApp.java (07-workers.md) against the test server. */
class WorkerAppTest {
  private TestWorkflowEnvironment env;
  private BankActivitiesImpl activities;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    // The same registration as WorkerApp.main, on the same Task Queue.
    Worker worker = env.newWorker("transfers");
    worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
    activities = new BankActivitiesImpl();
    worker.registerActivitiesImplementations(activities);
    env.start();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private TransferWorkflow stub(String taskQueue, String workflowId) {
    return env.getWorkflowClient()
        .newWorkflowStub(
            TransferWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .setWorkflowId(workflowId)
                .build());
  }

  @Test
  void theWorkerRunsWorkflowsAndActivitiesFromItsTaskQueue() {
    assertEquals(
        "o-1 submitted (call 1 on this instance)",
        stub("transfers", "transfer-o-1").transfer("o-1"),
        "WorkerApp.java: a Worker polling transfers should run the Workflow and its"
            + " Activity");
  }

  @Test
  void workflowsGetANewInstancePerExecutionAndActivitiesShareOne() {
    stub("transfers", "transfer-a").transfer("a");
    String second = stub("transfers", "transfer-b").transfer("b");
    assertEquals(
        "b submitted (call 1 on this instance)",
        second,
        "WorkerApp.java: registerWorkflowImplementationTypes takes a class, and the Worker"
            + " creates a fresh instance for every Workflow Execution");
    assertEquals(
        2,
        activities.submitted.get(),
        "WorkerApp.java: registerActivitiesImplementations takes one instance, shared by every"
            + " Activity Task");
  }

  @Test
  void aTaskQueueTypoStartsTheWorkflowButNothingRunsIt() {
    TransferWorkflow typo = stub("transfer", "transfer-typo");
    WorkflowClient.start(typo::transfer, "typo");
    assertEquals(
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING,
        WorkflowStub.fromTyped(typo).describe().getStatus(),
        "07-workers.md: a Workflow started on a Task Queue no Worker polls starts fine and waits");
    assertEquals(
        0,
        activities.submitted.get(),
        "07-workers.md: nothing runs a Workflow on a Task Queue the Worker does not poll");
    WorkflowStub.fromTyped(typo).terminate("test done");
  }
}
