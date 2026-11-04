package io.temporal.devdays.essentials.activities;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;

/** Not on a slide: the Workflow the Activity anatomy's runtime test drives. */
@WorkflowInterface
public interface OrderWorkflow {
  @WorkflowMethod
  OrderResult submit(Order order);

  class Impl implements OrderWorkflow {
    private final OrderActivities activities =
        Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(
                    RetryOptions.newBuilder().setInitialInterval(Duration.ofMillis(200)).build())
                .build());

    @Override
    public OrderResult submit(Order order) {
      return activities.submitOrder(order);
    }
  }
}
