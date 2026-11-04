package io.temporal.devdays.essentials.patterns4;

import io.temporal.activity.ActivityOptions;
import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.TriageWorkflow;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class TriageWorkflowImpl implements TriageWorkflow {
  private final ClaimActivities activities =
      Workflow.newActivityStub(
          ClaimActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

  // slide: 04-design-patterns.md "Keep it in the Workflow if it… makes business decisions"
  @Override
  public ApprovalStatus triage(Claim claim) {
    if (claim.amount() > 10_000 || claim.type() == ClaimType.SURGERY) {
      return ApprovalStatus.REQUIRES_REVIEW;
    }

    // continue to Activities...
    return activities.autoApprove(claim);
  }
  // end slide
}
