package io.temporal.devdays.essentials.serialization;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Returns its argument: one trip through the data converter each way. */
@WorkflowInterface
public interface EchoWorkflow {
  @WorkflowMethod
  Visit echo(Visit visit);
}
