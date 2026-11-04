package io.temporal.devdays.essentials.antipatterns;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface WireActivities {
  /** Asks the payment network once: "SETTLED" or "PENDING". */
  String checkSettlement(String wireId);

  /** Checks once, and fails (retryably) if the wire has not settled yet. */
  void awaitSettlement(String wireId);
}
