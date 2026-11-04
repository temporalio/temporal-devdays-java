package io.temporal.devdays.essentials.tuning;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class SubmitWorkflowImpl implements SubmitWorkflow {

  /** The Activity implementation the tuned Worker registers. */
  public static class Activities implements SubmitActivities {
    @Override
    public String submitOrder(String orderId) {
      return "submitted " + orderId;
    }
  }

  private final SubmitActivities activities =
      Workflow.newActivityStub(
          SubmitActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

  @Override
  public String submit(String orderId) {
    return activities.submitOrder(orderId);
  }
}
