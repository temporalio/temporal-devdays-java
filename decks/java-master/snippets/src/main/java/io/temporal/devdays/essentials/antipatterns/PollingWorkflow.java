package io.temporal.devdays.essentials.antipatterns;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Waits for a wire to settle. Two implementations: the ❌ loop and the ✅ Retry Policy. */
@WorkflowInterface
public interface PollingWorkflow {
  @WorkflowMethod
  void awaitWire(String wireId);
}
