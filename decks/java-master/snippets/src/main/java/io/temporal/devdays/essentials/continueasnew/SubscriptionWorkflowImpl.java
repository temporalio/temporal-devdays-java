package io.temporal.devdays.essentials.continueasnew;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class SubscriptionWorkflowImpl implements SubscriptionWorkflow {
  // slide: 15-continue-as-new.md "Continue-As-New in practice"
  private final SubscriptionActivities activities = Workflow.newActivityStub(
      SubscriptionActivities.class,
      ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(5)).build());

  @Override
  public void run(SubscriptionInput input) {
    int cyclesCompleted = input.cyclesCompleted();
    while (true) {
      activities.chargeSubscription(input.subscriptionId(), cyclesCompleted + 1);
      cyclesCompleted++;
      Workflow.sleep(Duration.ofDays(30));

      if (Workflow.getInfo().isContinueAsNewSuggested()) {
        Workflow.continueAsNew(new SubscriptionInput(input.subscriptionId(), cyclesCompleted));
      }
    }
  }
  // end slide
}
