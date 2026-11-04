package io.temporal.devdays.essentials.why;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

// part: 01-why-temporal.md "The Temporal version" 2
// FulfillmentWorkflowImpl.java
public class FulfillmentWorkflowImpl implements FulfillmentWorkflow {
  private final FulfillmentActivities activities =
      Workflow.newActivityStub(
          FulfillmentActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofSeconds(10))
              .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(4).build())
              .build());

  @Override
  public String fulfill(String orderId) {
    return activities.fulfillOrder(orderId);
  }
}
// end part
