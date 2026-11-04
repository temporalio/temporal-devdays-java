package io.temporal.devdays.essentials.errors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.activity.ActivityOptions;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkflowImplementationOptions;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The error-handling slides (24-error-handling.md), run on the in-memory test server. */
class ErrorHandlingTest {

  private static final String TASK_QUEUE = "errors";
  private static final Order ORDER = new Order("o-4");

  private TestWorkflowEnvironment env;
  private Worker worker;
  private WorkflowClient client;

  /** Counts attempts without changing the slide's Activity. */
  static class CountedPaymentActivities extends PaymentActivitiesImpl {
    final AtomicInteger attempts = new AtomicInteger();

    @Override
    public String chargePayment(Order order) {
      attempts.incrementAndGet();
      return super.chargePayment(order);
    }
  }

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    worker = env.newWorker(TASK_QUEUE);
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private <T> T stub(Class<T> workflow) {
    return client.newWorkflowStub(
        workflow,
        WorkflowOptions.newBuilder()
            .setTaskQueue(TASK_QUEUE)
            .setWorkflowId(UUID.randomUUID().toString())
            .build());
  }

  @Test
  void nonRetryablePaymentErrorRunsOnceAndTheWorkflowTakesTheOtherPath() {
    CountedPaymentActivities activities = new CountedPaymentActivities();
    worker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
    worker.registerActivitiesImplementations(activities);
    env.start();

    String result = stub(PaymentWorkflow.class).pay(ORDER);

    assertEquals(
        "new card requested",
        result,
        "PaymentWorkflowImpl.pay should catch the PaymentError and call requestNewCard,"
            + " as the 'Handling an ActivityFailure in a Workflow' slide shows");
    assertEquals(
        1,
        activities.attempts.get(),
        "PaymentActivitiesImpl.chargePayment throws setNonRetryable(true), so the slide claims"
            + " it runs exactly once");
  }

  @Test
  void applicationFailureFailsTheWorkflowAndTheClientSeesWorkflowFailedException() {
    worker.registerWorkflowImplementationTypes(CriticalWorkflowImpl.class);
    env.start();

    WorkflowFailedException e =
        assertThrows(
            WorkflowFailedException.class,
            () -> stub(CriticalWorkflow.class).process(ORDER),
            "CriticalWorkflowImpl throws ApplicationFailure: the slide claims that fails the"
                + " Workflow Execution, and the client gets WorkflowFailedException");
    ApplicationFailure cause =
        assertInstanceOf(
            ApplicationFailure.class,
            e.getCause(),
            "WorkflowFailedException.getCause() should be the Workflow's ApplicationFailure");
    assertEquals("CriticalError", cause.getType());
  }

  @Test
  void plainExceptionFailsTheWorkflowTaskAndTheWorkflowStaysOpen() throws Exception {
    worker.registerWorkflowImplementationTypes(BuggyWorkflowImpl.class);
    env.start();

    BuggyWorkflow workflow = stub(BuggyWorkflow.class);
    String id = WorkflowClient.start(workflow::process, ORDER).getWorkflowId();

    long failedTasks = 0;
    for (int i = 0; i < 50 && failedTasks == 0; i++) {
      Thread.sleep(100);
      failedTasks =
          client.fetchHistory(id).getEvents().stream()
              .filter(ev -> ev.getEventType() == EventType.EVENT_TYPE_WORKFLOW_TASK_FAILED)
              .count();
    }
    assertTrue(
        failedTasks > 0,
        "BuggyWorkflowImpl throws IllegalArgumentException: the notes claim that fails the"
            + " Workflow Task (WorkflowTaskFailed in history)");
    WorkflowStub untyped = client.newUntypedWorkflowStub(id);
    assertEquals(
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING,
        untyped.describe().getStatus(),
        "a plain exception must leave the Workflow Running, retrying its Workflow Task");
    untyped.terminate("test done");
  }

  @Test
  void failWorkflowExceptionTypesMakesAPlainExceptionFailTheWorkflow() {
    worker.registerWorkflowImplementationTypes(
        StrictWorkflowImpl.OPTIONS, StrictWorkflowImpl.class);
    env.start();

    WorkflowFailedException e =
        assertThrows(
            WorkflowFailedException.class,
            () -> stub(StrictWorkflow.class).process(ORDER),
            "setFailWorkflowExceptionTypes(InvalidOrderException.class) should make"
                + " StrictWorkflowImpl fail the Workflow, as the notes claim");
    assertTrue(e.getCause().getMessage().contains("invalid order o-4"));
  }

  @Test
  void failedChildArrivesAsChildWorkflowFailureWithTheChildsApplicationFailure() {
    worker.registerWorkflowImplementationTypes(ParentWorkflowImpl.class, CriticalWorkflowImpl.class);
    env.start();

    assertEquals(
        "child failed: CriticalError",
        stub(ParentWorkflow.class).run(ORDER),
        "ParentWorkflowImpl should catch ChildWorkflowFailure whose cause is the child's"
            + " ApplicationFailure, as the notes claim");
  }

  @Test
  void unhandledActivityFailureRethrownFailsTheWorkflowWithTheFullCauseChain() {
    PaymentActivities rejecting =
        order -> {
          throw ApplicationFailure.newNonRetryableFailure("boom", "GatewayDown");
        };
    worker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
    worker.registerActivitiesImplementations(rejecting);
    env.start();

    WorkflowFailedException e =
        assertThrows(WorkflowFailedException.class, () -> stub(PaymentWorkflow.class).pay(ORDER));
    ActivityFailure activityFailure =
        assertInstanceOf(
            ActivityFailure.class,
            e.getCause(),
            "PaymentWorkflowImpl rethrows the ActivityFailure: the slide claims the Workflow"
                + " fails with it");
    ApplicationFailure cause =
        assertInstanceOf(ApplicationFailure.class, activityFailure.getCause());
    assertEquals("GatewayDown", cause.getType());
  }

  @Test
  void anyOtherActivityExceptionArrivesAsApplicationFailureNamedAfterItsClass() {
    PaymentActivities throwing =
        order -> {
          throw new IllegalStateException("connection reset");
        };
    // One attempt, so the default Retry Policy does not retry it forever.
    WorkflowImplementationOptions oneAttempt =
        WorkflowImplementationOptions.newBuilder()
            .setActivityOptions(
                Map.of(
                    "ChargePayment",
                    ActivityOptions.newBuilder()
                        .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                        .build()))
            .build();
    worker.registerWorkflowImplementationTypes(oneAttempt, PaymentWorkflowImpl.class);
    worker.registerActivitiesImplementations(throwing);
    env.start();

    WorkflowFailedException e =
        assertThrows(WorkflowFailedException.class, () -> stub(PaymentWorkflow.class).pay(ORDER));
    ApplicationFailure cause =
        assertInstanceOf(ApplicationFailure.class, e.getCause().getCause());
    assertEquals(
        "java.lang.IllegalStateException",
        cause.getType(),
        "the notes claim any other exception from an Activity becomes an ApplicationFailure"
            + " whose type is the exception's class name");
  }
}
