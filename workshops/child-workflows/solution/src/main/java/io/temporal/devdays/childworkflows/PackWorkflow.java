package io.temporal.devdays.childworkflows;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.List;

/** Challenges 3 and 4, the parent: fans out one PackItemWorkflow per item, then fans in. */
@WorkflowInterface
public interface PackWorkflow {
    /** Returns one box per item, in item order. */
    @WorkflowMethod
    List<String> pack(Order order);
}
