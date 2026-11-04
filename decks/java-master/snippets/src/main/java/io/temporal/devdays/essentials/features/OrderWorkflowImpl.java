package io.temporal.devdays.essentials.features;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.Priority;
import io.temporal.workflow.Workflow;
import java.time.Duration;

// slide: 22-more-features.md "heading: Priority and Fairness in Java"
public class OrderWorkflowImpl implements OrderWorkflow {
  @Override
  public String processOrder(Order order) {
    OrderActivities activities =
        Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setPriority(
                    Priority.newBuilder()
                        .setPriorityKey(1)
                        .setFairnessKey(order.tenantId())
                        .setFairnessWeight(2)
                        .build())
                .build());
    return activities.chargePayment(order);
  }
}
// end slide
