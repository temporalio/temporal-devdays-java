package io.temporal.devdays.essentials.concepts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.WorkerFactory;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 03-temporal-concepts.md "One Worker, both kinds of code": one Worker on the {@code transfers}
 * Task Queue runs the Workflow by class and the Activities by instance.
 */
class WorkerRunsBothTest {
  private TestWorkflowEnvironment env;
  private WorkerFactory factory;

  @BeforeEach
  void setUp() {
    TransferActivitiesImpl.CALLS.clear();
    env = TestWorkflowEnvironment.newInstance();
    factory = WorkerRunsBoth.start(env.getWorkflowClient());
  }

  @AfterEach
  void tearDown() {
    factory.shutdownNow();
    env.close();
  }

  @Test
  void theOneWorkerRunsTheWorkflowAndItsActivities() {
    WorkflowClient client = env.getWorkflowClient();
    TransferWorkflow workflow =
        client.newWorkflowStub(
            TransferWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("transfers").setWorkflowId("t-1").build());

    String result = workflow.transfer("t-1");

    assertEquals(
        "withdrawn,deposited",
        result,
        "WorkerRunsBoth.java: the Worker polling \"transfers\" should run TransferWorkflowImpl to"
            + " completion");
    assertEquals(
        List.of("withdraw t-1", "deposit t-1"),
        TransferActivitiesImpl.CALLS,
        "WorkerRunsBoth.java: the slide claims the same Worker runs the Activities it was handed,"
            + " in the order the Workflow calls them");
  }
}
