package io.temporal.devdays.essentials.patterns4;

import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.FollowUpWorkflow;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class FollowUpWorkflowImpl implements FollowUpWorkflow {
  private String response;

  @Override
  public String followUp() {
    // slide: 04-design-patterns.md "Keep it in the Workflow if it… manages timing"
    // Sleep for 30 days — durable, costs nothing while waiting
    Workflow.sleep(Duration.ofDays(30));

    // Wait for a customer response — survives restarts
    Workflow.await(() -> response != null);
    // end slide
    return response;
  }

  @Override
  public void respond(String response) {
    this.response = response;
  }
}
