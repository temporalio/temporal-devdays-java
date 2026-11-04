package io.temporal.devdays.essentials.workers;

import io.temporal.activity.ActivityInterface;

/** The Activities the Worker in 07-workers.md registers. Not on a slide. */
@ActivityInterface
public interface BankActivities {
  String submitTransfer(String transferId);
}
