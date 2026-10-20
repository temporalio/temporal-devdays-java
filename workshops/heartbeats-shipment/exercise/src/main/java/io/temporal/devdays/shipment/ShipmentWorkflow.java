package io.temporal.devdays.shipment;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** One shipment, packed by one long-running Activity. */
@WorkflowInterface
public interface ShipmentWorkflow {
    @WorkflowMethod
    ShipmentResult run(ShipmentInput input);
}
