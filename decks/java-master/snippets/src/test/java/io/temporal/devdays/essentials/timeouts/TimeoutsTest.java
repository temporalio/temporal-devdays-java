package io.temporal.devdays.essentials.timeouts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.failure.v1.Failure;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.RetryOptions;
import io.temporal.devdays.essentials.timeouts.RetryScenarios.MaxAttemptsWorkflow;
import io.temporal.devdays.essentials.timeouts.RetryScenarios.MaxAttemptsWorkflowImpl;
import io.temporal.devdays.essentials.timeouts.RetryScenarios.NoTimeoutWorkflow;
import io.temporal.devdays.essentials.timeouts.RetryScenarios.NoTimeoutWorkflowImpl;
import io.temporal.devdays.essentials.timeouts.RetryScenarios.NonRetryableWorkflow;
import io.temporal.devdays.essentials.timeouts.RetryScenarios.NonRetryableWorkflowImpl;
import io.temporal.internal.common.ProtobufTimeUtils;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 23-timeouts-and-retries.md: the timeouts and Retry Policies do what the slides say. */
class TimeoutsTest {
  private static final String TASK_QUEUE = "timeouts";
  private static final Order ORDER = new Order("o-1");

  private TestWorkflowEnvironment env;
  private WorkflowClient client;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    Worker worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(
        OrderWorkflowImpl.class,
        MaxAttemptsWorkflowImpl.class,
        NonRetryableWorkflowImpl.class,
        NoTimeoutWorkflowImpl.class);
    worker.registerActivitiesImplementations(new OrderActivitiesImpl());
    env.start();
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private static String id() {
    return "timeouts-" + UUID.randomUUID();
  }

  private static WorkflowOptions options(String workflowId) {
    return WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).setWorkflowId(workflowId).build();
  }

  private List<HistoryEvent> history(String workflowId, Predicate<HistoryEvent> filter) {
    return client.fetchHistory(workflowId).getEvents().stream().filter(filter).toList();
  }

  @Test
  void theClientSetsTheWorkflowExecutionTimeout() {
    String workflowId = id();
    OrderStarter.start(client, ORDER, TASK_QUEUE, workflowId);
    assertEquals(
        "charged-o-1",
        client.newUntypedWorkflowStub(workflowId).getResult(String.class),
        "OrderStarter.java: the started Workflow should charge the order");
    HistoryEvent started =
        history(workflowId, HistoryEvent::hasWorkflowExecutionStartedEventAttributes).get(0);
    assertEquals(
        Duration.ofDays(30),
        ProtobufTimeUtils.toJavaDuration(
            started.getWorkflowExecutionStartedEventAttributes().getWorkflowExecutionTimeout()),
        "OrderStarter.java: setWorkflowExecutionTimeout(Duration.ofDays(30)) should reach the Service");
  }

  @Test
  void theWorkflowSetsTheActivitysStartToCloseTimeout() {
    String workflowId = id();
    OrderWorkflow workflow = client.newWorkflowStub(OrderWorkflow.class, options(workflowId));
    assertEquals("charged-o-1", workflow.processOrder(ORDER), "OrderWorkflowImpl.java");
    HistoryEvent scheduled =
        history(workflowId, HistoryEvent::hasActivityTaskScheduledEventAttributes).get(0);
    assertEquals(
        Duration.ofSeconds(10),
        ProtobufTimeUtils.toJavaDuration(
            scheduled.getActivityTaskScheduledEventAttributes().getStartToCloseTimeout()),
        "OrderWorkflowImpl.java: the stub's setStartToCloseTimeout(10s) should be on ActivityTaskScheduled");
  }

  @Test
  void maximumAttemptsFailedAttemptsAreNotEvents() {
    String workflowId = id();
    MaxAttemptsWorkflow workflow =
        client.newWorkflowStub(MaxAttemptsWorkflow.class, options(workflowId));
    String seen = workflow.charge(ORDER);
    assertEquals(
        "RETRY_STATE_MAXIMUM_ATTEMPTS_REACHED:java.lang.IllegalStateException",
        seen,
        "RetryScenarios.java MaxAttemptsWorkflowImpl: the ActivityFailure should report"
            + " RETRY_STATE_MAXIMUM_ATTEMPTS_REACHED, caused by the last attempt's error");
    assertEquals(
        1,
        history(workflowId, HistoryEvent::hasActivityTaskScheduledEventAttributes).size(),
        "one ActivityTaskScheduled for the whole Activity");
    List<HistoryEvent> started =
        history(workflowId, HistoryEvent::hasActivityTaskStartedEventAttributes);
    assertEquals(1, started.size(), "the slide says failed attempts are retries, not Events");
    assertEquals(
        3,
        started.get(0).getActivityTaskStartedEventAttributes().getAttempt(),
        "ActivityTaskStarted carries the final attempt's number");
    assertEquals(
        1,
        history(workflowId, HistoryEvent::hasActivityTaskFailedEventAttributes).size(),
        "one ActivityTaskFailed, for the last attempt");
  }

  @Test
  void setDoNotRetryStopsAfterOneAttemptAndTheCauseKeepsItsType() {
    String workflowId = id();
    NonRetryableWorkflow workflow =
        client.newWorkflowStub(NonRetryableWorkflow.class, options(workflowId));
    assertEquals(
        "RETRY_STATE_NON_RETRYABLE_FAILURE:CardDeclined",
        workflow.charge(ORDER),
        "RetryScenarios.java NonRetryableWorkflowImpl: setDoNotRetry(\"CardDeclined\") should stop"
            + " retries, and the cause's getType() should be CardDeclined");
    assertEquals(
        1,
        history(workflowId, HistoryEvent::hasActivityTaskStartedEventAttributes)
            .get(0)
            .getActivityTaskStartedEventAttributes()
            .getAttempt(),
        "the non-retryable failure should end the Activity on attempt 1");
  }

  /** A failure's message and every cause's, outermost first. */
  private static String messages(Failure failure) {
    StringBuilder all = new StringBuilder(failure.getMessage());
    for (Failure f = failure; f.hasCause(); f = f.getCause()) {
      all.append(" <- ").append(f.getCause().getMessage());
    }
    return all.toString();
  }

  @Test
  void anActivityWithNeitherCloseTimeoutFailsTheWorkflowTask() throws InterruptedException {
    String workflowId = id();
    NoTimeoutWorkflow workflow =
        client.newWorkflowStub(NoTimeoutWorkflow.class, options(workflowId));
    WorkflowClient.start(workflow::charge, ORDER);
    String message = "";
    for (int i = 0; i < 50 && message.isEmpty(); i++) {
      Thread.sleep(100);
      message =
          history(workflowId, HistoryEvent::hasWorkflowTaskFailedEventAttributes).stream()
              .map(e -> messages(e.getWorkflowTaskFailedEventAttributes().getFailure()))
              .findFirst()
              .orElse("");
    }
    assertTrue(
        message.contains("Both StartToCloseTimeout and ScheduleToCloseTimeout aren't specified"),
        "RetryScenarios.java NoTimeoutWorkflowImpl: the speaker notes say the call is refused and"
            + " the Workflow Task fails. Got: "
            + message);
    assertEquals(
        WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING,
        client.newUntypedWorkflowStub(workflowId).describe().getStatus(),
        "a failed Workflow Task leaves the Workflow running, retrying the task");
    client.newUntypedWorkflowStub(workflowId).terminate("test done");
  }

  @Test
  void theRetryPolicyDefaultsAreTheOnesTheSlideLists() {
    RetryOptions defaults = RetryOptions.newBuilder().validateBuildWithDefaults();
    assertEquals(Duration.ofSeconds(1), defaults.getInitialInterval(), "Initial Interval 1 second");
    assertEquals(2.0, defaults.getBackoffCoefficient(), "Backoff Coefficient 2.0");
    assertEquals(0, defaults.getMaximumAttempts(), "Maximum Attempts 0, which means unlimited");

    RetryOptions spelledOut = RetryScenarios.spelledOutDefaults();
    assertEquals(defaults.getInitialInterval(), spelledOut.getInitialInterval());
    assertEquals(defaults.getBackoffCoefficient(), spelledOut.getBackoffCoefficient());
    assertEquals(
        defaults.getInitialInterval().multipliedBy(100),
        spelledOut.getMaximumInterval(),
        "RetryScenarios.java: the default Maximum Interval is 100 x Initial Interval");
    assertEquals(List.of("CardDeclined"), List.of(spelledOut.getDoNotRetry()));

    assertEquals(0, RetryOptions.newBuilder().setMaximumAttempts(0).build().getMaximumAttempts());
    assertThrows(
        IllegalArgumentException.class,
        () -> RetryOptions.newBuilder().setMaximumAttempts(-1),
        "the speaker notes say a negative Maximum Attempts is refused");
  }
}
