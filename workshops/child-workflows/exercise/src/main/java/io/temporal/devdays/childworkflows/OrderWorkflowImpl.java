package io.temporal.devdays.childworkflows;

import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Workflow;

public class OrderWorkflowImpl implements OrderWorkflow {
    @Override
    public String placeOrder(Order order) {
        // A stub for a child, with its own Workflow ID. Calling its method starts
        // the child and waits for its result. The child gets its own Event History.
        ShipmentWorkflow shipment = Workflow.newChildWorkflowStub(
                ShipmentWorkflow.class,
                ChildWorkflowOptions.newBuilder()
                        .setWorkflowId("shipment-" + order.orderId())
                        .build());
        return shipment.ship(order.orderId());
    }
}
