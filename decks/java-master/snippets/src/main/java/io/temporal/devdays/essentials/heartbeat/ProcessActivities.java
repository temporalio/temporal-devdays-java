package io.temporal.devdays.essentials.heartbeat;

import io.temporal.activity.ActivityInterface;
import java.util.List;

@ActivityInterface
public interface ProcessActivities {
  /** Processes items one by one, and releases its locks if it is cancelled part way. */
  void processItems(List<String> items);
}
