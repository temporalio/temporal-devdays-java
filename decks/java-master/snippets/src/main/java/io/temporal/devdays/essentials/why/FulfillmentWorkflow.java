package io.temporal.devdays.essentials.why;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** The Workflow interface behind "The Temporal version" in 01-why-temporal.md. */
@WorkflowInterface
public interface FulfillmentWorkflow {
  @WorkflowMethod
  String fulfill(String orderId);
}
