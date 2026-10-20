package io.temporal.devdays.childworkflows;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Challenges 3 and 4, the child: packs one item into a box. */
@WorkflowInterface
public interface PackItemWorkflow {
    @WorkflowMethod
    String pack(String item);
}
