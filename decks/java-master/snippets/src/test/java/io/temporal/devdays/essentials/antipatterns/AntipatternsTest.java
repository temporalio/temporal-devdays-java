package io.temporal.devdays.essentials.antipatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 17b-patterns-and-anti-patterns.md: the four ❌/✅ slides, each half run and checked. */
class AntipatternsTest {
  private TestWorkflowEnvironment env;
  private WorkflowClient client;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private static WorkflowOptions options(String taskQueue, String id) {
    return WorkflowOptions.newBuilder().setTaskQueue(taskQueue).setWorkflowId(id).build();
  }

  private List<HistoryEvent> history(String workflowId) {
    return client.fetchHistory(workflowId).getEvents();
  }

  // --- The business ID is the Workflow ID ---

  @Test
  void aRandomWorkflowIdLetsADoubleTapStartTwoTransfers() {
    env.newWorker("transfers").registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
    env.start();
    Transfer transfer = new Transfer("t-1");

    WorkflowExecution a = TransferStarts.startWithRandomId(client, transfer);
    WorkflowExecution b = TransferStarts.startWithRandomId(client, transfer);

    assertNotEquals(
        a.getWorkflowId(),
        b.getWorkflowId(),
        "TransferStarts.startWithRandomId: two starts of one transfer should run as two Workflows");
  }

  @Test
  void theBusinessIdAsWorkflowIdRejectsTheSecondStartWhileTheFirstRuns() {
    env.newWorker("transfers").registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
    env.start();
    Transfer transfer = new Transfer("t-2");

    WorkflowExecution first = TransferStarts.startWithTransferId(client, transfer);

    assertEquals("transfer-t-2", first.getWorkflowId());
    assertThrows(
        WorkflowExecutionAlreadyStarted.class,
        () -> TransferStarts.startWithTransferId(client, transfer),
        "TransferStarts.startWithTransferId: a second start with the same transfer ID should throw"
            + " WorkflowExecutionAlreadyStarted");
  }

  // --- An idempotency key that survives retries ---

  @Test
  void theRunIdAndActivityIdKeyIsTheSameOnTheRetrySoTheLedgerPostsOnce() {
    Ledger ledger = new Ledger();
    ledger.failOnceAfterCommit();
    Worker worker = env.newWorker("debits");
    worker.registerWorkflowImplementationTypes(DebitWorkflowImpl.class);
    worker.registerActivitiesImplementations(new DebitActivitiesImpl(ledger));
    env.start();

    client
        .newWorkflowStub(DebitWorkflow.class, options("debits", "debit-1"))
        .debit(new Debit("acct-1", 80));

    assertEquals(2, ledger.requests.size(), "the ledger should see the post and its retry");
    assertEquals(
        ledger.requests.get(0),
        ledger.requests.get(1),
        "DebitActivitiesImpl.postDebit: the retry should send the same idempotency key");
    assertEquals(1, ledger.postings.size(), "the ledger should post the debit exactly once");
  }

  // --- Polling an external system ---

  @Test
  void pollingInALoopAddsAnActivityAndATimerToHistoryOnEveryPass() {
    WireActivitiesImpl wires = new WireActivitiesImpl(4);
    Worker worker = env.newWorker("wires");
    worker.registerWorkflowImplementationTypes(LoopPollingWorkflowImpl.class);
    worker.registerActivitiesImplementations(wires);
    env.start();

    client.newWorkflowStub(PollingWorkflow.class, options("wires", "wire-1")).awaitWire("wire-1");

    List<HistoryEvent> events = history("wire-1");
    assertEquals(4, wires.settlementChecks.size(), "the wire settles on the fourth check");
    assertEquals(
        4,
        events.stream().filter(HistoryEvent::hasActivityTaskScheduledEventAttributes).count(),
        "LoopPollingWorkflowImpl: every check should be its own Activity in history");
    assertEquals(
        3,
        events.stream().filter(HistoryEvent::hasTimerStartedEventAttributes).count(),
        "LoopPollingWorkflowImpl: every wait should be its own Timer in history");
  }

  @Test
  void pollingWithTheRetryPolicyKeepsOneActivityInHistoryHoweverManyChecksItTakes() {
    WireActivitiesImpl wires = new WireActivitiesImpl(4);
    Worker worker = env.newWorker("wires");
    worker.registerWorkflowImplementationTypes(RetryPollingWorkflowImpl.class);
    worker.registerActivitiesImplementations(wires);
    env.start();

    client.newWorkflowStub(PollingWorkflow.class, options("wires", "wire-2")).awaitWire("wire-2");

    List<HistoryEvent> events = history("wire-2");
    assertEquals(4, wires.settlementChecks.size(), "the wire settles on the fourth check");
    assertEquals(
        1,
        events.stream().filter(HistoryEvent::hasActivityTaskScheduledEventAttributes).count(),
        "RetryPollingWorkflowImpl: retries are not recorded, so history holds one Activity");
    assertEquals(
        0,
        events.stream().filter(HistoryEvent::hasTimerStartedEventAttributes).count(),
        "RetryPollingWorkflowImpl: the Retry Policy waits, so there are no Timers");
  }

  // --- Sleeping in an Activity ---

  @Test
  void aThreeDayHoldIsOneDurableTimerInTheWorkflow() {
    Bank bank = new Bank();
    Worker worker = env.newWorker("deposits");
    worker.registerWorkflowImplementationTypes(DepositHoldWorkflowImpl.class);
    worker.registerActivitiesImplementations(new DepositActivitiesImpl(bank));
    env.start();

    client
        .newWorkflowStub(DepositHoldWorkflow.class, options("deposits", "deposit-1"))
        .hold(new Deposit("dep-1"));

    assertEquals(List.of("dep-1"), bank.released, "DepositHoldWorkflowImpl should release dep-1");
    List<HistoryEvent> timers =
        history("deposit-1").stream().filter(HistoryEvent::hasTimerStartedEventAttributes).toList();
    assertEquals(1, timers.size(), "DepositHoldWorkflowImpl: the hold should be one Timer");
    assertEquals(
        Duration.ofDays(3).toSeconds(),
        timers.get(0).getTimerStartedEventAttributes().getStartToFireTimeout().getSeconds(),
        "DepositHoldWorkflowImpl: Workflow.sleep(Duration.ofDays(3)) should be a three-day Timer");
  }
}
