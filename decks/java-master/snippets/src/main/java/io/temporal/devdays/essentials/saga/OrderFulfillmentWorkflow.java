package io.temporal.devdays.essentials.saga;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderFulfillmentWorkflow {
  @WorkflowMethod
  OrderResult fulfill(Order order);
}
