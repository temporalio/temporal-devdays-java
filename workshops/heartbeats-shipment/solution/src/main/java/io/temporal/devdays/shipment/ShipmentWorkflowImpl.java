package io.temporal.devdays.shipment;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ShipmentWorkflowImpl implements ShipmentWorkflow {
    private final ShipmentActivities activities = Workflow.newActivityStub(
            ShipmentActivities.class,
            ActivityOptions.newBuilder()
                    // A ceiling for the whole shipment, which takes about 12 seconds.
                    .setStartToCloseTimeout(Duration.ofSeconds(20))
                    // TODO 1a start: give up on an attempt when no Heartbeat arrives for 2 seconds
                    .setHeartbeatTimeout(Duration.ofSeconds(2))
                    // TODO 1a end
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setMaximumAttempts(10)
                            .build())
                    .build());

    @Override
    public ShipmentResult run(ShipmentInput input) {
        return activities.packShipment(input);
    }
}
