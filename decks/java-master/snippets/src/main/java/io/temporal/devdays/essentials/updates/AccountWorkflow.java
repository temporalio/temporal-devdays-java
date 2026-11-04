package io.temporal.devdays.essentials.updates;

import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

// slide: 11-updates.md "Java Updates"
@WorkflowInterface
public interface AccountWorkflow {
  @WorkflowMethod
  void run(int opening);

  @UpdateMethod
  int withdraw(int amount);

  @UpdateValidatorMethod(updateName = "withdraw")
  void validateWithdraw(int amount);
}
// end slide
