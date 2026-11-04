package io.temporal.devdays.essentials.versioning;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * One Workflow Type, four implementations: the code as it was, the patched code, the patch
 * retired, and the version check deleted. Every one registers as Workflow Type "MyWorkflow".
 */
@WorkflowInterface
public interface MyWorkflow {
  @WorkflowMethod
  void run();
}
