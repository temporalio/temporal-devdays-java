package io.temporal.devdays.essentials.client;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

// The Client from 07b-client.md. Compiled, never run as main: it would
// connect to localhost:7233. run(client) holds the same calls so
// TransferClientTest can hand it the test server's Client.
//
// slide: 07b-client.md "Java Client"
// TransferClient.java
public class TransferClient {
  public static void main(String[] args) {
    WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget("localhost:7233").build());
    WorkflowClient client = WorkflowClient.newInstance(service);
    run(client);
  }

  static void run(WorkflowClient client) {
    Transfer transfer = new Transfer("tx-1001", "A-1", "B-2", 250);
    TransferWorkflow workflow = client.newWorkflowStub(
        TransferWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("transfers")
            .setWorkflowId("transfer-" + transfer.transferId())
            .build());

    Receipt receipt = workflow.transfer(transfer);
    System.out.println(receipt.status());
  }
}
// end slide
