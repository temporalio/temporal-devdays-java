package io.temporal.devdays.essentials.errors;

import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.ChildWorkflowFailure;
import io.temporal.workflow.Workflow;

/**
 * Speaker notes: a failed Child Workflow arrives as a {@link ChildWorkflowFailure}, with the
 * child's failure as its cause.
 */
public class ParentWorkflowImpl implements ParentWorkflow {
  @Override
  public String run(Order order) {
    CriticalWorkflow child = Workflow.newChildWorkflowStub(CriticalWorkflow.class);
    try {
      child.process(order);
      return "child succeeded";
    } catch (ChildWorkflowFailure e) {
      if (e.getCause() instanceof ApplicationFailure af) {
        return "child failed: " + af.getType();
      }
      throw e;
    }
  }
}
