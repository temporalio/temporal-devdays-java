package io.temporal.devdays.essentials.features;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** What the Schedules slide starts every morning. Not on a slide. */
@WorkflowInterface
public interface ReportWorkflow {
  @WorkflowMethod
  String buildDailyReport();
}
