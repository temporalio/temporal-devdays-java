package io.temporal.devdays.essentials.workflows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** 05-workflows: the transfer Workflow on the anatomy slides runs and does what the notes say. */
@Timeout(30)
class TransferWorkflowTest {
  private static final String QUEUE = "transfers";

  private TestWorkflowEnvironment env;
  private WorkflowClient client;
  private BankActivitiesImpl bank;

  private final Transfer transfer = new Transfer("tx-1", "acct-a", "acct-b", 250);

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    client = env.getWorkflowClient();
    bank = new BankActivitiesImpl();
    Worker worker = env.newWorker(QUEUE);
    worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
    worker.registerActivitiesImplementations(bank);
    env.start();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private WorkflowOptions options() {
    return WorkflowOptions.newBuilder()
        .setTaskQueue(QUEUE)
        .setWorkflowId(UUID.randomUUID().toString())
        .build();
  }

  private Receipt run(Transfer t) {
    return client.newWorkflowStub(TransferWorkflow.class, options()).transfer(t);
  }

  @Test
  void theTransferMovesTheMoneyAndReturnsAReceipt() {
    assertEquals(
        Receipt.completed(),
        run(transfer),
        "TransferWorkflowImpl.transfer should return a completed Receipt");
    assertEquals(-250L, bank.balances.get("acct-a"), "withdraw should take 250 from acct-a");
    assertEquals(250L, bank.balances.get("acct-b"), "deposit should put 250 in acct-b");
  }

  @Test
  void theWorkflowTypeIsTheInterfaceName() {
    // Started by name alone, as a Client in another codebase would.
    WorkflowStub untyped = client.newUntypedWorkflowStub("TransferWorkflow", options());
    untyped.start(transfer);
    assertEquals(
        "completed",
        untyped.getResult(Receipt.class).status(),
        "05-workflows.md (Java Workflow interface): the Workflow Type is the interface's simple"
            + " name, TransferWorkflow");
  }

  @Test
  void aFlaggedTransferIsRejectedBeforeAnyMoneyMoves() {
    Receipt receipt = run(new Transfer("tx-2", "acct-a", "acct-b", 50_000));
    assertEquals(
        "rejected",
        receipt.status(),
        "TransferWorkflowImpl.transfer: the if on checkFraud should reject a 50,000 transfer");
    assertTrue(bank.balances.isEmpty(), "a rejected transfer should move no money");
  }

  @Test
  void aDepositThatFailsForGoodIsCompensatedWithARefund() {
    bank.closed.add("acct-b");
    Receipt receipt = run(transfer);
    assertEquals(
        "refunded",
        receipt.status(),
        "05-workflows.md (Java Workflow implementation): a non-retryable deposit failure reaches"
            + " the catch as an ActivityFailure and the Workflow refunds");
    assertTrue(
        Optional.ofNullable(receipt.reason()).orElse("").contains("activityType='Deposit'"),
        "the ActivityFailure message names the Activity Type, Deposit: " + receipt.reason());
    assertEquals(0L, bank.balances.get("acct-a"), "refund should put the 250 back in acct-a");
    assertFalse(bank.balances.containsKey("acct-b"), "acct-b is closed and never got the money");
  }
}
