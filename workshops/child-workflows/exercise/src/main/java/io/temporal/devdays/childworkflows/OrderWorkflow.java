package io.temporal.devdays.childworkflows;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Challenge 1, the parent: runs one ShipmentWorkflow child and waits for it. */
@WorkflowInterface
public interface OrderWorkflow {
    @WorkflowMethod
    String placeOrder(Order order);
}
