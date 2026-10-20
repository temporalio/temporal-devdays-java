package io.temporal.devdays.childworkflows;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Challenge 2: starts itself as a child, one level shallower each time. */
@WorkflowInterface
public interface ChainWorkflow {
    /** Returns how many levels of children ran below this one. */
    @WorkflowMethod
    int run(ChainRequest request);
}
