package io.temporal.devdays.essentials.errors;

import io.temporal.worker.WorkflowImplementationOptions;

/**
 * Speaker notes: {@code setFailWorkflowExceptionTypes} turns a plain exception into a Workflow
 * failure. Register with {@link #OPTIONS}.
 */
public class StrictWorkflowImpl implements StrictWorkflow {

  public static class InvalidOrderException extends RuntimeException {
    public InvalidOrderException(String message) {
      super(message);
    }
  }

  public static final WorkflowImplementationOptions OPTIONS =
      WorkflowImplementationOptions.newBuilder()
          .setFailWorkflowExceptionTypes(InvalidOrderException.class)
          .build();

  @Override
  public OrderResult process(Order order) {
    throw new InvalidOrderException("invalid order " + order.orderId());
  }
}
