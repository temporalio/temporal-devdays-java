package io.temporal.devdays.essentials.observability;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;

// slide: 25-observability.md "Logging from a Workflow"
public class OrderWorkflowImpl implements OrderWorkflow {
  private static final Logger log = Workflow.getLogger(OrderWorkflowImpl.class);

  private final OrderActivities activities =
      Workflow.newActivityStub(
          OrderActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

  @Override
  public String processOrder(String orderId) {
    log.info("Order received: {}", orderId);
    String receipt = activities.chargeCard(orderId);
    log.info("Order charged: {} {}", orderId, receipt);
    return receipt;
  }
}
// end slide
