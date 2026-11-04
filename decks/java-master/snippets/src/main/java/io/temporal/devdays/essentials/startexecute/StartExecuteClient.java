package io.temporal.devdays.essentials.startexecute;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;

/** The client code from 08-start-vs-execute.md, wrapped in methods so the test can hand it a Client. */
public final class StartExecuteClient {
  private StartExecuteClient() {}

  public static OrderResult executeOrder(WorkflowClient client, Order order) {
    // slide: 08-start-vs-execute.md "fire and wait"
    OrderWorkflow workflow = client.newWorkflowStub(
        OrderWorkflow.class,
        WorkflowOptions.newBuilder()
            .setWorkflowId("order-" + order.orderId())
            .setTaskQueue("order-task-queue")
            .build());

    OrderResult result = workflow.processOrder(order);
    System.out.println("Result: " + result.status());
    // end slide
    return result;
  }

  public static OrderResult startApproval(WorkflowClient client, Order order) {
    // slide: 08-start-vs-execute.md "fire, and keep the handle"
    ApprovalWorkflow workflow = client.newWorkflowStub(
        ApprovalWorkflow.class,
        WorkflowOptions.newBuilder()
            .setWorkflowId("approval-" + order.orderId())
            .setTaskQueue("order-task-queue")
            .build());
    WorkflowClient.start(workflow::run, order);

    System.out.println("Status: " + workflow.getStatus());
    workflow.approve("alice");

    OrderResult result = WorkflowStub.fromTyped(workflow).getResult(OrderResult.class);
    System.out.println("Result: " + result.status());
    // end slide
    return result;
  }
}
