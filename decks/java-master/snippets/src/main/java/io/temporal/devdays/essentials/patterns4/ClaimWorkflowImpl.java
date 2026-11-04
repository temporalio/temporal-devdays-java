package io.temporal.devdays.essentials.patterns4;

import io.temporal.activity.ActivityOptions;
import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.ClaimWorkflow;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ClaimWorkflowImpl implements ClaimWorkflow {
  @Override
  public Approval process(Claim claim) {
    // slide: 04-design-patterns.md "Keep it in the Workflow if it… must survive restarts"
    ActivityOptions options =
        ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build();
    ClaimActivities activities = Workflow.newActivityStub(ClaimActivities.class, options);

    Approval approval = activities.getApproval(claim);
    // end slide
    return approval;
  }
}
