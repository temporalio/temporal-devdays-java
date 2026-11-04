package io.temporal.devdays.essentials.serialization;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ClaimWorkflow {
  @WorkflowMethod
  ClaimResult process(Claim claim);
}
