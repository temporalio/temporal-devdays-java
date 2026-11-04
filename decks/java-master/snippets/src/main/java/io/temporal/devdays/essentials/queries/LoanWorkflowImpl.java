package io.temporal.devdays.essentials.queries;

// part: 10-queries.md "Java Queries" 2
public class LoanWorkflowImpl implements LoanWorkflow {
  private String status = "underwriting";

  @Override
  public LoanResult process(Loan loan) {
    status = "approved";
    return new LoanResult(loan.loanId(), status);
  }

  @Override
  public String getLoanStatus() {
    return status;
  }
}
// end part
