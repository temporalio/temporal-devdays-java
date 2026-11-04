package io.temporal.devdays.essentials.startexecute;

import io.temporal.workflow.Workflow;

public class ApprovalWorkflowImpl implements ApprovalWorkflow {
  private String status = "awaiting approval";

  @Override
  public OrderResult run(Order order) {
    Workflow.await(() -> !status.equals("awaiting approval"));
    return new OrderResult(order.orderId(), status);
  }

  @Override
  public String getStatus() {
    return status;
  }

  @Override
  public void approve(String approver) {
    status = "approved by " + approver;
  }
}
