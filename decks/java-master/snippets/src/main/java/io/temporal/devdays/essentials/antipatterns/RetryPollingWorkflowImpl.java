package io.temporal.devdays.essentials.antipatterns;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** ✅ Polling with the Retry Policy. */
public class RetryPollingWorkflowImpl implements PollingWorkflow {
  @Override
  public void awaitWire(String wireId) {
    // part: 17b-patterns-and-anti-patterns.md "Polling an external system" 2
    // ✅ One Activity. The Retry Policy does the waiting
    WireActivities wires = Workflow.newActivityStub(
        WireActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofMinutes(1))
                .setBackoffCoefficient(1)
                .build())
            .build());
    wires.awaitSettlement(wireId);
    // end part
  }
}
