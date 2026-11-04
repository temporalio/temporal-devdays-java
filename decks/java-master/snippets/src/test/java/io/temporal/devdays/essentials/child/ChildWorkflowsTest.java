package io.temporal.devdays.essentials.child;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionDescription;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 14-child-workflows.md: "Spawning a child, waiting for the result". */
class ChildWorkflowsTest {
  private static final String TASK_QUEUE = "child-workflows";

  private TestWorkflowEnvironment env;
  private WorkflowClient client;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    Worker worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class, ShipmentWorkflowImpl.class);
    env.start();
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  @Test
  void theChildReturnsItsResultToTheParentUnderItsOwnWorkflowId() {
    OrderWorkflow order =
        client.newWorkflowStub(
            OrderWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).setWorkflowId("order-o-1").build());

    String tracking = order.placeOrder(new Order("o-1", List.of("box-7")));

    assertEquals(
        "TRACK-o-1",
        tracking,
        "OrderWorkflowImpl.placeOrder: calling shipment.ship(...) should block until the child"
            + " returns, and hand its result to the parent");

    // The child is its own Workflow Execution, findable by the ID the parent chose.
    WorkflowExecutionDescription child = client.newUntypedWorkflowStub("shipment-o-1").describe();
    assertEquals(
        "ShipmentWorkflow",
        child.getWorkflowType(),
        "OrderWorkflowImpl.placeOrder: the child's Workflow Type is its interface name");
    assertEquals(
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED,
        child.getStatus(),
        "OrderWorkflowImpl.placeOrder: the child 'shipment-o-1' should have completed on its own");
    assertEquals(
        "order-o-1",
        child.getParentExecution().getWorkflowId(),
        "OrderWorkflowImpl.placeOrder: the child should record the order Workflow as its parent");
  }
}
