package io.temporal.devdays.essentials.timeouts;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import java.time.Duration;

/** The Workflow timeouts slide in 23-timeouts-and-retries.md: the Client sets them. */
public final class OrderStarter {
  private OrderStarter() {}

  public static void start(
      WorkflowClient client, Order order, String taskQueue, String workflowId) {
    // slide: 23-timeouts-and-retries.md "# Workflow timeouts"
    OrderWorkflow workflow =
        client.newWorkflowStub(
            OrderWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(taskQueue)
                .setWorkflowId(workflowId)
                .setWorkflowExecutionTimeout(Duration.ofDays(30))
                .build());
    WorkflowClient.start(workflow::processOrder, order);
    // end slide
  }
}
