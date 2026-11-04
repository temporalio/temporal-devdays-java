package io.temporal.devdays.essentials.serialization;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** Reports the zone of the visit as the Workflow received it. */
@WorkflowInterface
public interface InspectVisitWorkflow {
  @WorkflowMethod
  VisitCheck inspect(Visit visit);
}
