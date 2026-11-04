package io.temporal.devdays.essentials.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.client.WorkflowUpdateException;
import io.temporal.client.WorkflowUpdateHandle;
import io.temporal.client.WorkflowUpdateStage;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs AccountWorkflowImpl.java and LockedAccountWorkflowImpl.java (11-updates.md). */
class UpdatesTest {
  private TestWorkflowEnvironment env;
  private WorkflowClient client;
  private AccountActivitiesImpl activities;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    client = env.getWorkflowClient();
    activities = new AccountActivitiesImpl();
  }

  @AfterEach
  void tearDown() {
    activities.release();
    env.close();
  }

  private AccountWorkflow start(Class<? extends AccountWorkflow> impl, String workflowId) {
    Worker worker = env.newWorker("accounts");
    worker.registerWorkflowImplementationTypes(impl);
    worker.registerActivitiesImplementations(activities);
    env.start();
    AccountWorkflow account =
        client.newWorkflowStub(
            AccountWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("accounts").setWorkflowId(workflowId).build());
    WorkflowClient.start(account::run, 100);
    return account;
  }

  private static WorkflowUpdateHandle<Integer> startWithdraw(AccountWorkflow account, int amount) {
    return WorkflowStub.fromTyped(account)
        .startUpdate("withdraw", WorkflowUpdateStage.ACCEPTED, Integer.class, amount);
  }

  private long count(String workflowId, java.util.function.Predicate<HistoryEvent> p) {
    return client.fetchHistory(workflowId).getEvents().stream().filter(p).count();
  }

  @Test
  void anUpdateReturnsAValueAndTheValidatorRejectsBeforeTheHandlerRuns() {
    AccountWorkflow account = start(AccountWorkflowImpl.class, "account-1");
    assertEquals(
        70, account.withdraw(30), "AccountWorkflowImpl.withdraw should return the new balance");
    assertThrows(
        WorkflowUpdateException.class,
        () -> account.withdraw(500),
        "AccountWorkflowImpl.validateWithdraw should reject a withdrawal over the balance");
    assertEquals(50, account.withdraw(20), "AccountWorkflowImpl.withdraw: 70 - 20");
    assertEquals(
        0,
        UpdatesClient.withdrawFifty(account),
        "UpdatesClient.java: account.withdraw(50) should wait for and return the new balance");
    WorkflowStub.fromTyped(account).getResult(Void.class);

    assertEquals(
        List.of(30, 20, 50),
        activities.debited,
        "11-updates.md: the rejected Update never reached the handler, so never debited");
    assertEquals(
        3,
        count("account-1", HistoryEvent::hasWorkflowExecutionUpdateAcceptedEventAttributes),
        "11-updates.md: a rejected Update leaves nothing in Event History");
    assertEquals(
        3,
        count("account-1", HistoryEvent::hasWorkflowExecutionUpdateCompletedEventAttributes),
        "11-updates.md: each accepted Update's result is recorded as UpdateCompleted");
  }

  @Test
  void startUpdateReturnsOnceTheUpdateIsAcceptedAndTheResultComesLater() {
    AccountWorkflow account = start(AccountWorkflowImpl.class, "account-2");
    activities.hold();
    WorkflowUpdateHandle<Integer> update = startWithdraw(account, 10);
    // Accepted, but the handler is still waiting on its Activity.
    assertEquals(
        List.of(),
        activities.debited,
        "11-updates.md: startUpdate with ACCEPTED should return before the handler finishes");
    activities.release();
    assertEquals(
        90, update.getResult(), "11-updates.md: the update handle's getResult is the new balance");
    WorkflowStub.fromTyped(account).terminate("test done");
  }

  @Test
  void twoConcurrentWithdrawalsCanBothPassTheValidatorAndOverdrawTheAccount() {
    AccountWorkflow account = start(AccountWorkflowImpl.class, "account-3");
    activities.hold();
    WorkflowUpdateHandle<Integer> a = startWithdraw(account, 80);
    WorkflowUpdateHandle<Integer> b = startWithdraw(account, 80);
    activities.release();
    assertEquals(
        List.of(-60, 20),
        Stream.of(a.getResult(), b.getResult()).sorted().toList(),
        "11-updates.md speaker notes: both withdraw(80) calls pass the validator while the first"
            + " handler waits on its Activity, and the account ends at -60");
    assertEquals(List.of(80, 80), activities.debited, "both handlers debited the account");
    WorkflowStub.fromTyped(account).terminate("test done");
  }

  @Test
  void aWorkflowLockAndARecheckInTheHandlerStopTheOverdraft() {
    AccountWorkflow account = start(LockedAccountWorkflowImpl.class, "account-4");
    activities.hold();
    WorkflowUpdateHandle<Integer> a = startWithdraw(account, 80);
    WorkflowUpdateHandle<Integer> b = startWithdraw(account, 80);
    activities.release();
    assertEquals(20, a.getResult(), "LockedAccountWorkflowImpl.withdraw: the first withdrawal");
    CompletionException failed = assertThrows(CompletionException.class, () -> b.getResultAsync().join(),
        "LockedAccountWorkflowImpl.withdraw: the second withdrawal should fail once it holds the"
            + " lock and re-checks the balance");
    assertInstanceOf(
        WorkflowUpdateException.class,
        failed.getCause(),
        "LockedAccountWorkflowImpl.withdraw: the caller should get a WorkflowUpdateException");
    assertInstanceOf(
        ApplicationFailure.class,
        failed.getCause().getCause(),
        "LockedAccountWorkflowImpl.withdraw: its cause is the ApplicationFailure the handler threw");
    assertEquals(List.of(80), activities.debited, "only the first withdrawal debited");
    assertEquals(
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING,
        WorkflowStub.fromTyped(account).describe().getStatus(),
        "11-updates.md: an ApplicationFailure in the handler fails only that Update, not the"
            + " Workflow");
    WorkflowStub.fromTyped(account).terminate("test done");
  }
}
