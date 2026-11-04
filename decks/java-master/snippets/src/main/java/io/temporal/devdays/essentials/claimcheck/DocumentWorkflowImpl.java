package io.temporal.devdays.essentials.claimcheck;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class DocumentWorkflowImpl implements DocumentWorkflow {
  private final DocumentActivities activities =
      Workflow.newActivityStub(
          DocumentActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

  // slide: 21-claim-check.md "The Workflow holds only the key"
  @Override
  public String process(String url) {
    String key = activities.downloadDocument(url);
    try {
      return activities.summarizeDocument(key);
    } finally {
      activities.deleteDocument(key);
    }
  }
  // end slide
}
