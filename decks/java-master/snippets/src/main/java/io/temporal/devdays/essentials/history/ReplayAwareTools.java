package io.temporal.devdays.essentials.history;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.List;

/**
 * Not on a slide: every row of the "More replay-aware methods" table, used once, so the test can
 * check each does what the table says.
 */
public final class ReplayAwareTools {
  private ReplayAwareTools() {}

  @ActivityInterface
  public interface Pricing {
    int price(String sku);
  }

  public record Results(
      int total, String firstToFinish, boolean signalArrived, long startedAt, int dice) {}

  @WorkflowInterface
  public interface ToolsWorkflow {
    @WorkflowMethod
    Results run(List<String> skus);
  }

  public static class ToolsWorkflowImpl implements ToolsWorkflow {
    private final Pricing pricing =
        Workflow.newActivityStub(
            Pricing.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

    private boolean signalled;

    @Override
    public Results run(List<String> skus) {
      long startedAt = Workflow.currentTimeMillis();
      int dice = Workflow.newRandom().nextInt(6) + 1;

      // Async.function + Promise.allOf: Activities in parallel
      List<Promise<Integer>> prices =
          skus.stream().map(sku -> Async.function(pricing::price, sku)).toList();
      Promise.allOf(prices).get();
      int total = prices.stream().mapToInt(Promise::get).sum();

      // Promise.anyOf with Workflow.newTimer: whichever finishes first
      Promise<String> quick = Workflow.newTimer(Duration.ofMinutes(1)).thenApply(v -> "quick");
      Promise<String> slow = Workflow.newTimer(Duration.ofMinutes(5)).thenApply(v -> "slow");
      String first = Promise.anyOf(List.of(quick, slow)).get();

      // Workflow.await with a timeout: false when time runs out first
      boolean arrived = Workflow.await(Duration.ofMinutes(10), () -> signalled);

      Workflow.sleep(Duration.ofHours(1));
      return new Results(total, first, arrived, startedAt, dice);
    }
  }
}
