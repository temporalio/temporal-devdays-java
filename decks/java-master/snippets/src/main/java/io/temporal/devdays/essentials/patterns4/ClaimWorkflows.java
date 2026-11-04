package io.temporal.devdays.essentials.patterns4;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** The Workflow interfaces behind the "Keep it in the Workflow" slides. Not on a slide. */
public final class ClaimWorkflows {
  private ClaimWorkflows() {}

  @WorkflowInterface
  public interface TriageWorkflow {
    @WorkflowMethod
    ApprovalStatus triage(Claim claim);
  }

  @WorkflowInterface
  public interface LoanWorkflow {
    @WorkflowMethod
    Decision adjudicate(Applicant applicant);
  }

  @WorkflowInterface
  public interface FollowUpWorkflow {
    @WorkflowMethod
    String followUp();

    @SignalMethod
    void respond(String response);
  }

  @WorkflowInterface
  public interface RiskWorkflow {
    @WorkflowMethod
    double score(String email, Claim claim);
  }

  @WorkflowInterface
  public interface ClaimWorkflow {
    @WorkflowMethod
    Approval process(Claim claim);
  }

  @WorkflowInterface
  public interface SignupWorkflow {
    @WorkflowMethod
    User signUp(UserData data);
  }
}
