package io.temporal.devdays.essentials.errors;

/** Speaker notes: a plain exception fails the Workflow Task, not the Workflow. */
public class BuggyWorkflowImpl implements BuggyWorkflow {
  @Override
  public OrderResult process(Order order) {
    throw new IllegalArgumentException("invalid order " + order.orderId());
  }
}
