package io.temporal.devdays.essentials.queries;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

// part: 10-queries.md "Java Queries" 1
@WorkflowInterface
public interface LoanWorkflow {
  @WorkflowMethod
  LoanResult process(Loan loan);

  @QueryMethod
  String getLoanStatus();
}
// end part
