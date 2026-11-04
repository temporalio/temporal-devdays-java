package io.temporal.devdays.essentials.claimcheck;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface DocumentWorkflow {
  @WorkflowMethod
  String process(String url);
}
