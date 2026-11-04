package io.temporal.devdays.essentials.errors;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface PaymentWorkflow {
  @WorkflowMethod
  String pay(Order order);
}
