package io.temporal.devdays.essentials.startexecute;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Finishes on its own: the shape calling the Workflow method suits. The slides in
 * 08-start-vs-execute.md show only the client side, so none of this is on a slide.
 */
@WorkflowInterface
public interface OrderWorkflow {
  @WorkflowMethod
  OrderResult processOrder(Order order);
}
