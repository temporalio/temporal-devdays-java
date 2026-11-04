package io.temporal.devdays.essentials.child;

import io.temporal.api.enums.v1.ParentClosePolicy;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Workflow;

public class OrderWorkflowImpl implements OrderWorkflow {
  // slide: 14-child-workflows.md "Spawning a child"
  @Override
  public String placeOrder(Order order) {
    ShipmentWorkflow shipment = Workflow.newChildWorkflowStub(
        ShipmentWorkflow.class,
        ChildWorkflowOptions.newBuilder()
            .setWorkflowId("shipment-" + order.orderId())
            .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_ABANDON)
            .build());
    return shipment.ship(order.orderId());
  }
  // end slide
}
