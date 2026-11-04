package io.temporal.devdays.essentials.workflows;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

// slide: 05-workflows.md "heading: Java Workflow interface"
// TransferWorkflow.java
@WorkflowInterface
public interface TransferWorkflow {
  @WorkflowMethod
  Receipt transfer(Transfer transfer);
}
// end slide
