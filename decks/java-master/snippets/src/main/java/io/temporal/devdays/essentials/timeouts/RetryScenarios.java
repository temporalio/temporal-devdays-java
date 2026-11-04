package io.temporal.devdays.essentials.timeouts;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;

/**
 * The Retry Policy diagrams and speaker notes in 23-timeouts-and-retries.md, as Workflows the tests
 * run. Each returns what the Workflow saw when the Activity gave up: "retryState:causeType".
 */
public final class RetryScenarios {
  private RetryScenarios() {}

  /** The Retry Policies slide's speaker notes: the defaults spelled out. */
  public static RetryOptions spelledOutDefaults() {
    // slide: 23-timeouts-and-retries.md "The Java shape, with the defaults spelled out"
    RetryOptions retryOptions =
        RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(1))
            .setBackoffCoefficient(2.0)
            .setMaximumInterval(Duration.ofSeconds(100))
            // setMaximumAttempts: leave it unset (or 0) for unlimited; 1 means no retries
            .setDoNotRetry("CardDeclined")
            .build();
    // end slide
    return retryOptions;
  }

  static String failureOf(ActivityFailure failure) {
    String type =
        failure.getCause() instanceof ApplicationFailure app
            ? app.getType()
            : failure.getCause().getClass().getSimpleName();
    return failure.getRetryState().name() + ":" + type;
  }

  @WorkflowInterface
  public interface MaxAttemptsWorkflow {
    @WorkflowMethod
    String charge(Order order);
  }

  /** The Maximum Attempts diagram: three attempts, default intervals. */
  public static class MaxAttemptsWorkflowImpl implements MaxAttemptsWorkflow {
    private final OrderActivities capped =
        Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                .build());

    @Override
    public String charge(Order order) {
      try {
        return capped.chargeFlakyPayment(order);
      } catch (ActivityFailure e) {
        return failureOf(e);
      }
    }
  }

  @WorkflowInterface
  public interface NonRetryableWorkflow {
    @WorkflowMethod
    String charge(Order order);
  }

  /** The Non-retryable diagram's second form: the caller lists the type. */
  public static class NonRetryableWorkflowImpl implements NonRetryableWorkflow {
    private final OrderActivities callerDecides =
        Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder().setDoNotRetry("CardDeclined").build())
                .build());

    @Override
    public String charge(Order order) {
      try {
        return callerDecides.chargeDeclinedCard(order);
      } catch (ActivityFailure e) {
        return failureOf(e);
      }
    }
  }

  @WorkflowInterface
  public interface NoTimeoutWorkflow {
    @WorkflowMethod
    String charge(Order order);
  }

  /** Speaker notes: with neither Start-To-Close nor Schedule-To-Close, the call is refused. */
  public static class NoTimeoutWorkflowImpl implements NoTimeoutWorkflow {
    @Override
    public String charge(Order order) {
      OrderActivities untimed =
          Workflow.newActivityStub(OrderActivities.class, ActivityOptions.newBuilder().build());
      return untimed.chargePayment(order);
    }
  }
}
