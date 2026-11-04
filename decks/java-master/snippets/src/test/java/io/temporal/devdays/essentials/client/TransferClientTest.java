package io.temporal.devdays.essentials.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the Client from TransferClient.java (07b-client.md) against the test server. */
class TransferClientTest {
  private TestWorkflowEnvironment env;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    Worker worker = env.newWorker("transfers");
    worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
    env.start();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  @Test
  void theClientStartsTheWorkflowWaitsAndPrintsTheReceipt() {
    PrintStream out = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new PrintStream(captured));
    try {
      TransferClient.run(env.getWorkflowClient());
    } finally {
      System.setOut(out);
    }
    assertTrue(
        captured.toString().contains("COMPLETED"),
        "TransferClient.java: calling workflow.transfer(...) should wait for the Workflow and"
            + " print the Receipt's status");
  }

  @Test
  void theWorkflowIdIsBuiltFromTheTransferId() {
    WorkflowClient client = env.getWorkflowClient();
    TransferClient.run(client);
    Receipt receipt =
        client.newUntypedWorkflowStub("transfer-tx-1001").getResult(Receipt.class);
    assertEquals(
        "tx-1001",
        receipt.transferId(),
        "TransferClient.java: the Workflow ID should be transfer-<transferId>, so support can"
            + " find the Workflow by transfer number");
    assertEquals(
        "TransferWorkflow",
        WorkflowStub.fromTyped(client.newWorkflowStub(TransferWorkflow.class, "transfer-tx-1001"))
            .describe()
            .getWorkflowType(),
        "TransferClient.java: the Workflow Type comes from the interface name");
  }
}
