package io.temporal.devdays.essentials.patterns4;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.List;
import java.util.UUID;

/**
 * Not on a slide: one ID from the Workflow API and one from the JDK. A Query on a closed Workflow
 * replays it, which shows which of the two survives replay.
 */
@WorkflowInterface
public interface IdsWorkflow {
  @WorkflowMethod
  List<String> ids();

  @QueryMethod
  List<String> idsAfterReplay();

  class Impl implements IdsWorkflow {
    private List<String> ids;

    @Override
    public List<String> ids() {
      ids = List.of(Workflow.randomUUID().toString(), UUID.randomUUID().toString());
      return ids;
    }

    @Override
    public List<String> idsAfterReplay() {
      return ids;
    }
  }
}
