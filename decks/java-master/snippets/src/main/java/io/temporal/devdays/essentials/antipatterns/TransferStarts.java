package io.temporal.devdays.essentials.antipatterns;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.util.UUID;

/** The two starts on the business-ID slide. Each returns the execution it started. */
public final class TransferStarts {
  private TransferStarts() {}

  public static WorkflowExecution startWithRandomId(WorkflowClient client, Transfer transfer) {
    // part: 17b-patterns-and-anti-patterns.md "The business ID is the Workflow ID" 1
    // ❌ A random ID: a double-tapped "Send" starts two transfers
    TransferWorkflow random = client.newWorkflowStub(
        TransferWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("transfers")
            .setWorkflowId(UUID.randomUUID().toString())
            .build());
    WorkflowClient.start(random::transfer, transfer);
    // end part
    return WorkflowStub.fromTyped(random).getExecution();
  }

  public static WorkflowExecution startWithTransferId(WorkflowClient client, Transfer transfer) {
    // part: 17b-patterns-and-anti-patterns.md "The business ID is the Workflow ID" 2
    // ✅ The transfer's own ID: the second start is rejected
    TransferWorkflow byId = client.newWorkflowStub(
        TransferWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("transfers")
            .setWorkflowId("transfer-" + transfer.transferId())
            .build());
    WorkflowClient.start(byId::transfer, transfer);
    // end part
    return WorkflowStub.fromTyped(byId).getExecution();
  }
}
