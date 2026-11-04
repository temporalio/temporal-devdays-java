package io.temporal.devdays.essentials.timeouts;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class OrderWorkflowImpl implements OrderWorkflow {
  // slide: 23-timeouts-and-retries.md "# Activity timeouts"
  private final OrderActivities activities =
      Workflow.newActivityStub(
          OrderActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());
  // end slide

  @Override
  public String processOrder(Order order) {
    return activities.chargePayment(order);
  }
}
