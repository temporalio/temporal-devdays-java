package io.temporal.devdays.essentials.encryption;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** A Workflow that has never heard of encryption. */
@WorkflowInterface
public interface GreetWorkflow {
  @WorkflowMethod
  String greet(String name);
}
