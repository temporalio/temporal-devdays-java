package io.temporal.devdays.essentials.antipatterns;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** The idempotency slide's Activity, retried after the ledger has already committed. */
public class DebitWorkflowImpl implements DebitWorkflow {
  private final DebitActivities activities = Workflow.newActivityStub(
      DebitActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(10))
          .setRetryOptions(RetryOptions.newBuilder().setInitialInterval(Duration.ofSeconds(1)).build())
          .build());

  @Override
  public String debit(Debit debit) {
    return activities.postDebit(debit);
  }
}
