package io.temporal.devdays.essentials.startexecute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the client code from StartExecuteClient.java (08-start-vs-execute.md). */
class StartExecuteTest {
  private TestWorkflowEnvironment env;
  private WorkflowClient client;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    Worker worker = env.newWorker("order-task-queue");
    worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class, ApprovalWorkflowImpl.class);
    env.start();
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private ApprovalWorkflow approval(String workflowId) {
    return client.newWorkflowStub(
        ApprovalWorkflow.class,
        WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue("order-task-queue")
            .build());
  }

  @Test
  void callingTheWorkflowMethodStartsItAndWaitsForTheResult() {
    OrderResult result = StartExecuteClient.executeOrder(client, new Order("o-1"));
    assertEquals(
        "submitted",
        result.status(),
        "StartExecuteClient.executeOrder: calling processOrder on the stub should return the"
            + " Workflow's result");
  }

  @Test
  void callingTheWorkflowMethodIsStartPlusGetResult() {
    OrderWorkflow workflow =
        client.newWorkflowStub(
            OrderWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("order-task-queue").build());
    WorkflowClient.start(workflow::processOrder, new Order("o-2"));
    assertEquals(
        "submitted",
        WorkflowStub.fromTyped(workflow).getResult(OrderResult.class).status(),
        "08-start-vs-execute.md: WorkflowClient.start plus getResult should give the same result"
            + " as calling the Workflow method");
  }

  @Test
  void startReturnsAStubThatCanQuerySignalAndWaitForTheResult() {
    PrintStream out = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured));
    OrderResult result;
    try {
      result = StartExecuteClient.startApproval(client, new Order("o-3"));
    } finally {
      System.setOut(out);
    }
    assertEquals(
        new OrderResult("o-3", "approved by alice"),
        result,
        "StartExecuteClient.startApproval: the Signal should unblock the Workflow");
    assertTrue(
        captured.toString().contains("Status: awaiting approval"),
        "StartExecuteClient.startApproval: the Query should read the running Workflow's state");
  }

  @Test
  void startReturnsWhileTheWorkflowIsStillRunning() {
    ApprovalWorkflow workflow = approval("approval-o-4");
    WorkflowClient.start(workflow::run, new Order("o-4"));
    assertEquals(
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING,
        WorkflowStub.fromTyped(workflow).describe().getStatus(),
        "08-start-vs-execute.md: WorkflowClient.start returns once the Workflow is started, not"
            + " when it finishes");
    WorkflowStub.fromTyped(workflow).terminate("test done");
  }

  @Test
  void aStubCanBeRebuiltFromTheWorkflowIdAlone() {
    WorkflowClient.start(approval("approval-o-5")::run, new Order("o-5"));

    ApprovalWorkflow rebuilt = client.newWorkflowStub(ApprovalWorkflow.class, "approval-o-5");
    assertEquals(
        "awaiting approval",
        rebuilt.getStatus(),
        "08-start-vs-execute.md: a stub built from the Workflow ID alone should Query the Workflow");
    rebuilt.approve("bob");
    assertEquals(
        "approved by bob",
        WorkflowStub.fromTyped(rebuilt).getResult(OrderResult.class).status(),
        "08-start-vs-execute.md: a stub built from the Workflow ID alone should Signal it and"
            + " read its result");
  }

  @Test
  void aSecondStartWithARunningWorkflowsIdIsRefused() {
    ApprovalWorkflow first = approval("approval-o-6");
    WorkflowClient.start(first::run, new Order("o-6"));
    ApprovalWorkflow second = approval("approval-o-6");
    assertThrows(
        WorkflowExecutionAlreadyStarted.class,
        () -> WorkflowClient.start(second::run, new Order("o-6")),
        "08-start-vs-execute.md: Temporal should refuse a second start with the ID of a running"
            + " Workflow");
    WorkflowStub.fromTyped(first).terminate("test done");
  }

  @Test
  void withoutAWorkflowIdTheSdkGeneratesOne() {
    OrderWorkflow workflow =
        client.newWorkflowStub(
            OrderWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("order-task-queue").build());
    String id = WorkflowClient.start(workflow::processOrder, new Order("o-7")).getWorkflowId();
    assertTrue(
        !id.isEmpty(),
        "08-start-vs-execute.md: the Workflow ID is optional in Java; the SDK generates a UUID");
  }
}
