package io.temporal.devdays.essentials.versioning;

import io.temporal.activity.ActivityInterface;

/** The three Activities the versioning slides schedule. Each does nothing; only order matters. */
@ActivityInterface
public interface VersioningActivities {
  void activityA();

  void activityB();

  void activityC();
}
