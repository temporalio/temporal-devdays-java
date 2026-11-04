package io.temporal.devdays.essentials.heartbeat;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.client.ActivityCanceledException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProcessActivitiesImpl implements ProcessActivities {
  /** One entry per time the cleanup ran. */
  public final List<Boolean> locksReleased = new CopyOnWriteArrayList<>();

  /** How many items were processed before the Activity stopped. */
  public final List<String> processed = new CopyOnWriteArrayList<>();

  private void processItem(String item) {
    try {
      Thread.sleep(200);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw Activity.wrap(e);
    }
    processed.add(item);
  }

  private void releaseLocks(List<String> items) {
    locksReleased.add(true);
  }

  // slide: 16-heartbeating.md "Heartbeating and cancellation"
  @Override
  public void processItems(List<String> items) {
    ActivityExecutionContext context = Activity.getExecutionContext();
    try {
      for (int index = 0; index < items.size(); index++) {
        processItem(items.get(index));
        context.heartbeat(index + 1);
      }
    } catch (ActivityCanceledException e) {
      releaseLocks(items);
      throw e;
    }
  }
  // end slide
}
