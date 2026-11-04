package io.temporal.devdays.essentials.serialization;

public class InspectVisitWorkflowImpl implements InspectVisitWorkflow {
  @Override
  public VisitCheck inspect(Visit visit) {
    return new VisitCheck(visit.at().getZone().getId(), visit);
  }
}
