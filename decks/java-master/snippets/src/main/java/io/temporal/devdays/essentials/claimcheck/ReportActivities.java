package io.temporal.devdays.essentials.claimcheck;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface ReportActivities {
  /** A 1 MB result: over the External Storage threshold, under the payload limit. */
  byte[] buildReport();
}
