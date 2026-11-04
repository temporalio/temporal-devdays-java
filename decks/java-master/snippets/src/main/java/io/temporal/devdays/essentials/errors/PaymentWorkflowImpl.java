package io.temporal.devdays.essentials.errors;

import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.failure.TimeoutFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class PaymentWorkflowImpl implements PaymentWorkflow {

  private final PaymentActivities payments =
      Workflow.newActivityStub(
          PaymentActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

  // slide: 24-error-handling.md "Handling an ActivityFailure in a Workflow"
  @Override
  public String pay(Order order) {
    try {
      return payments.chargePayment(order);
    } catch (ActivityFailure e) {
      Throwable cause = e.getCause();
      if (cause instanceof TimeoutFailure) {
        // the Activity timed out
      } else if (cause instanceof CanceledFailure) {
        // the Activity was canceled
      } else if (cause instanceof ApplicationFailure af && af.getType().equals("PaymentError")) {
        return requestNewCard(order);
      }
      throw e;
    }
  }
  // end slide

  private String requestNewCard(Order order) {
    return "new card requested";
  }
}
