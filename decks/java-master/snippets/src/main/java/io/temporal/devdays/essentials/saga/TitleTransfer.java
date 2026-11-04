package io.temporal.devdays.essentials.saga;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Saga whose undo needs what its forward step returned: the refund needs the payment's
 * confirmation, so it can only be registered after the payment. No slide shows this code; it backs
 * the "register after" side of the comparison in 17-saga-04-when-to-register.md.
 */
public final class TitleTransfer {
  private TitleTransfer() {}

  /** Every step and undo that ran, in order. */
  public static final List<String> JOURNAL = Collections.synchronizedList(new ArrayList<>());

  public record TitleOrder(String orderId, String failAt) {}

  @ActivityInterface
  public interface TitleActivities {
    void checkFraud(TitleOrder order);

    String processPayment(TitleOrder order);

    void refundPayment(TitleOrder order, String confirmation);

    void transferTitle(TitleOrder order);

    void revertTitleTransfer(TitleOrder order);
  }

  public static class TitleActivitiesImpl implements TitleActivities {
    @Override
    public void checkFraud(TitleOrder order) {
      JOURNAL.add("checkFraud");
    }

    @Override
    public String processPayment(TitleOrder order) {
      JOURNAL.add("processPayment");
      return "conf-" + order.orderId();
    }

    @Override
    public void refundPayment(TitleOrder order, String confirmation) {
      JOURNAL.add("refundPayment:" + confirmation);
    }

    @Override
    public void transferTitle(TitleOrder order) {
      JOURNAL.add("transferTitle");
      if ("transferTitle".equals(order.failAt())) {
        throw ApplicationFailure.newNonRetryableFailure("title transfer failed", "TitleTransferFailed");
      }
    }

    @Override
    public void revertTitleTransfer(TitleOrder order) {
      JOURNAL.add("revertTitleTransfer");
    }
  }

  @WorkflowInterface
  public interface TitleTransferWorkflow {
    @WorkflowMethod
    void transfer(TitleOrder order);
  }

  public static class TitleTransferWorkflowImpl implements TitleTransferWorkflow {
    private final TitleActivities activities =
        Workflow.newActivityStub(
            TitleActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                .build());

    @Override
    public void transfer(TitleOrder order) {
      Saga saga = new Saga(new Saga.Options.Builder().setContinueWithError(true).build());
      try {
        activities.checkFraud(order);

        // The undo needs the confirmation, so it is registered after the step.
        String confirmation = activities.processPayment(order);
        saga.addCompensation(activities::refundPayment, order, confirmation);

        activities.transferTitle(order);
        saga.addCompensation(activities::revertTitleTransfer, order);
      } catch (ActivityFailure e) {
        saga.compensate();
        throw e;
      }
    }
  }
}
