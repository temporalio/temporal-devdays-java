package io.temporal.devdays.essentials.patterns4;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.devdays.essentials.patterns4.RfiWorkflows.ReviewWorkflow;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class ReviewWorkflowImpl implements ReviewWorkflow {
  private final RfiActivities activities =
      Workflow.newActivityStub(
          RfiActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofMinutes(1))
              .setRetryOptions(
                  RetryOptions.newBuilder().setInitialInterval(Duration.ofMillis(200)).build())
              .build());

  // part: 04-design-patterns.md "can fail on its own" 2
  // ReviewWorkflowImpl.java
  @Override
  public void review(RfiRequest req, String assignedReviewer) {
    activities.notifyReviewer(req, assignedReviewer);
  }
  // end part
}
