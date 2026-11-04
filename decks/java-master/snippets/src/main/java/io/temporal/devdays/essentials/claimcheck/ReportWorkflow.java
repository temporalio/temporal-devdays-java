package io.temporal.devdays.essentials.claimcheck;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Plain Workflow code: it handles the full 1 MB value and knows nothing of External Storage. */
@WorkflowInterface
public interface ReportWorkflow {
  @WorkflowMethod
  int reportLength();
}
