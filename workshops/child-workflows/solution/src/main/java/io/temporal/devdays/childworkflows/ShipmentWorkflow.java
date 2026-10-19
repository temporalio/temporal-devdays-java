package io.temporal.devdays.childworkflows;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Challenge 1, the child: ships one order and returns its tracking number. */
@WorkflowInterface
public interface ShipmentWorkflow {
    @WorkflowMethod
    String ship(String orderId);
}
