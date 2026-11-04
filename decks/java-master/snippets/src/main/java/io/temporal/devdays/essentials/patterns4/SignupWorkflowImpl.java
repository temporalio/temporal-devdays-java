package io.temporal.devdays.essentials.patterns4;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.SignupWorkflow;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** Not on a slide: calls createUser and lets the Retry Policy retry it. */
public class SignupWorkflowImpl implements SignupWorkflow {
  private final UserActivities activities =
      Workflow.newActivityStub(
          UserActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofSeconds(30))
              .setRetryOptions(
                  RetryOptions.newBuilder().setInitialInterval(Duration.ofMillis(200)).build())
              .build());

  @Override
  public User signUp(UserData data) {
    return activities.createUser(data);
  }
}
