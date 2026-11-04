package io.temporal.devdays.essentials.antipatterns;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** ✅ A three-day hold as a durable Timer, then a quick Activity. */
public class DepositHoldWorkflowImpl implements DepositHoldWorkflow {
  private final DepositActivities activities = Workflow.newActivityStub(
      DepositActivities.class,
      ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

  // part: 17b-patterns-and-anti-patterns.md "Sleeping in an Activity" 2
  // ✅ A durable Timer in the Workflow, then a quick Activity
  @Override
  public void hold(Deposit deposit) {
    Workflow.sleep(Duration.ofDays(3));
    activities.releaseFunds(deposit.depositId());
  }
  // end part
}
