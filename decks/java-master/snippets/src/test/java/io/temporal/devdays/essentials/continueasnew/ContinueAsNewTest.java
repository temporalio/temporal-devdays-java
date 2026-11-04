package io.temporal.devdays.essentials.continueasnew;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.CanceledFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 15-continue-as-new.md: "Continue-As-New in practice: a monthly subscription".
 *
 * <p>The slide sleeps 30 days between charges: the time-skipping test server skips the time.
 *
 * <p>The Continue-As-New branch itself is not exercised here. The Server decides when to suggest
 * it from history size, and the test server cannot be told to suggest it early. That branch is
 * covered by compiling the snippet and by SlidesMatchTest; changing the slide's code to make it
 * testable would teach the wrong shape.
 */
class ContinueAsNewTest {
  private static final String TASK_QUEUE = "continue-as-new";

  private TestWorkflowEnvironment env;
  private SubscriptionActivitiesImpl activities;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    Worker worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(SubscriptionWorkflowImpl.class);
    activities = new SubscriptionActivitiesImpl();
    worker.registerActivitiesImplementations(activities);
    env.start();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  @Test
  void billsMonthlyUntilCancelledAndCancellationReachesTheThirtyDaySleep() {
    WorkflowClient client = env.getWorkflowClient();
    SubscriptionWorkflow subscription =
        client.newWorkflowStub(
            SubscriptionWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).setWorkflowId("sub-1").build());
    WorkflowClient.start(subscription::run, new SubscriptionInput("sub-1", 0));

    // Charges land on day 0, 30 and 60; cancel during the third month.
    env.sleep(Duration.ofDays(75));
    WorkflowStub stub = WorkflowStub.fromTyped(subscription);
    stub.cancel();

    WorkflowFailedException e =
        assertThrows(
            WorkflowFailedException.class,
            () -> stub.getResult(Void.class),
            "SubscriptionWorkflowImpl.run never returns on its own: only cancellation ends it");
    assertInstanceOf(
        CanceledFailure.class,
        e.getCause(),
        "SubscriptionWorkflowImpl.run: cancelling should reach Workflow.sleep as a CanceledFailure");
    assertEquals(
        List.of(1, 2, 3),
        activities.charges,
        "SubscriptionWorkflowImpl.run should charge cycles 1, 2 and 3 in 75 days, carrying"
            + " cyclesCompleted forward");
  }
}
