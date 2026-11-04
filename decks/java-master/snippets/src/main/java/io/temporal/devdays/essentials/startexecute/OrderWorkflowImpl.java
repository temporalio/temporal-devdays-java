package io.temporal.devdays.essentials.startexecute;

public class OrderWorkflowImpl implements OrderWorkflow {
  @Override
  public OrderResult processOrder(Order order) {
    return new OrderResult(order.orderId(), "submitted");
  }
}
