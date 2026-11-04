package io.temporal.devdays.essentials.serialization;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ClaimWorkflowImpl implements ClaimWorkflow {
  private final ClaimActivities activities =
      Workflow.newActivityStub(
          ClaimActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

  // part: 19-serialization.md "Data serialization" 2
  @Override
  public ClaimResult process(Claim claim) {
    // `claim` was JSON on the wire a moment ago
    ClaimResult result = activities.processClaim(claim);

    // and this crosses the wire on the way back out
    return result;
  }
  // end part
}
