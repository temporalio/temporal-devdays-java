package io.temporal.devdays.essentials.patterns4;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** The three Workflows that call the RFI Activities. Not on a slide. */
public final class RfiWorkflows {
  private RfiWorkflows() {}

  @WorkflowInterface
  public interface EligibilityWorkflow {
    @WorkflowMethod
    boolean check(RfiRequest req);
  }

  @WorkflowInterface
  public interface ReviewWorkflow {
    @WorkflowMethod
    void review(RfiRequest req, String assignedReviewer);
  }

  @WorkflowInterface
  public interface AnnualReportWorkflow {
    @WorkflowMethod
    void run(RfiRequest req);
  }
}
