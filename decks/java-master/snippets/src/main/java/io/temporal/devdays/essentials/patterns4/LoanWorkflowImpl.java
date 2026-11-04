package io.temporal.devdays.essentials.patterns4;

import io.temporal.activity.ActivityOptions;
import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.LoanWorkflow;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class LoanWorkflowImpl implements LoanWorkflow {
  private final LoanActivities activities =
      Workflow.newActivityStub(
          LoanActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

  // slide: 04-design-patterns.md "Keep it in the Workflow if it… coordinates Activities"
  @Override
  public Decision adjudicate(Applicant applicant) {
    CreditCheck creditCheck = activities.runCreditCheck(applicant);
    FraudCheck fraud = activities.checkFraud(applicant, creditCheck);
    Decision decision = activities.makeDecision(applicant, creditCheck, fraud);

    return decision;
  }
  // end slide
}
