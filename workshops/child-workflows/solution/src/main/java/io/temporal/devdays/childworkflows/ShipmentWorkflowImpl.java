package io.temporal.devdays.childworkflows;

import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ShipmentWorkflowImpl implements ShipmentWorkflow {
    @Override
    public String ship(String orderId) {
        // No Activities in this Workshop: every child only sleeps on a durable
        // timer, so the Temporal UI shows the parent-child structure and nothing else.
        Workflow.sleep(Duration.ofSeconds(2));
        return "TRACK-" + orderId;
    }
}
