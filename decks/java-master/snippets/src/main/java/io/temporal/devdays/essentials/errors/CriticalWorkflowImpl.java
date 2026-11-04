package io.temporal.devdays.essentials.errors;

import io.temporal.failure.ApplicationFailure;

public class CriticalWorkflowImpl implements CriticalWorkflow {

  // slide: 24-error-handling.md "Failing a Workflow with ApplicationFailure"
  @Override
  public OrderResult process(Order order) {
    // Only ApplicationFailure should be thrown on purpose from Workflow code
    throw ApplicationFailure.newFailure("unrecoverable state", "CriticalError");
  }
  // end slide
}
