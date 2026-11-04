package io.temporal.devdays.essentials.patterns4;

import io.temporal.activity.ActivityOptions;
import io.temporal.devdays.essentials.patterns4.RfiWorkflows.EligibilityWorkflow;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class EligibilityWorkflowImpl implements EligibilityWorkflow {
  private final RfiActivities activities =
      Workflow.newActivityStub(
          RfiActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(1)).build());

  // part: 04-design-patterns.md "is worth testing in isolation" 2
  // EligibilityWorkflowImpl.java
  @Override
  public boolean check(RfiRequest req) {
    boolean isEligible = activities.validateEligibility(req);
    return isEligible;
  }
  // end part
}
