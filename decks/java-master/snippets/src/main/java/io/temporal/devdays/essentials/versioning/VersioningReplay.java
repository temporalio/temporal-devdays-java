package io.temporal.devdays.essentials.versioning;

import io.temporal.client.WorkflowClient;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.testing.WorkflowReplayer;

/** Replays a real execution's Event History against the current Workflow code. */
public final class VersioningReplay {
  private VersioningReplay() {}

  public static void replay(WorkflowClient client, String workflowId) throws Exception {
    // slide: 18-versioning.md "Catch it before you deploy"
    WorkflowExecutionHistory history = client.fetchHistory(workflowId);

    // Throws if the new code would make
    // different decisions from this history
    WorkflowReplayer.replayWorkflowExecution(history, MyWorkflowImpl.class);
    // end slide
  }
}
