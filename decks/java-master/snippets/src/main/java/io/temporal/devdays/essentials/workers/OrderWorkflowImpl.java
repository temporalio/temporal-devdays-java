package io.temporal.devdays.essentials.workers;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/**
 * Not on a slide. Registered by type: the Worker creates one instance per Workflow Execution, so
 * the instance count below is how WorkerAppTest proves it.
 */
public class OrderWorkflowImpl implements OrderWorkflow {
  private final OrderActivities activities =
      Workflow.newActivityStub(
          OrderActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

  private int calls = 0;

  @Override
  public String processOrder(String orderId) {
    calls++;
    return activities.submitOrder(orderId) + " (call " + calls + " on this instance)";
  }
}
