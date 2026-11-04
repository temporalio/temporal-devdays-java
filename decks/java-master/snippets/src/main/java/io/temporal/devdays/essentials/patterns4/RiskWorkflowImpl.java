package io.temporal.devdays.essentials.patterns4;

import io.temporal.devdays.essentials.patterns4.ClaimWorkflows.RiskWorkflow;

public class RiskWorkflowImpl implements RiskWorkflow {
  @Override
  public double score(String email, Claim claim) {
    // slide: 04-design-patterns.md "Keep it in the Workflow if it… is a pure calculation"
    boolean isValidEmail = email.matches("[^@\\s]+@company\\.com");

    double riskScore = claim.amount() / 1000 + claim.history().size();
    // end slide
    return isValidEmail ? riskScore : -1;
  }
}
