package io.temporal.devdays.essentials.heartbeat;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.List;

@WorkflowInterface
public interface ProcessWorkflow {
  @WorkflowMethod
  void run(List<String> items);
}
