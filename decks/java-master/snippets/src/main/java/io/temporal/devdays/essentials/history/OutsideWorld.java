package io.temporal.devdays.essentials.history;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;

/**
 * Not on a slide: the bug the Event History notes warn about. The Workflow branches on a value
 * read from outside it (here a static field standing in for a config file), so a replay after the
 * value changes produces a different Command than the history recorded.
 */
public final class OutsideWorld {
  private OutsideWorld() {}

  /** Stands in for a config file or an environment variable. Never read one in a Workflow. */
  public static volatile boolean useTimer = false;

  @ActivityInterface
  public interface Notifier {
    void notifyCustomer();
  }

  @WorkflowInterface
  public interface ConfigReadingWorkflow {
    @WorkflowMethod
    void run();
  }

  public static class ConfigReadingWorkflowImpl implements ConfigReadingWorkflow {
    private final Notifier notifier =
        Workflow.newActivityStub(
            Notifier.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

    @Override
    public void run() {
      if (useTimer) { // ❌ read from outside the Workflow
        Workflow.sleep(Duration.ofMinutes(1));
      } else {
        notifier.notifyCustomer();
      }
    }
  }
}
