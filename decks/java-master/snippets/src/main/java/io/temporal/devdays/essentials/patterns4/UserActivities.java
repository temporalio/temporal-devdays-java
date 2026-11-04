package io.temporal.devdays.essentials.patterns4;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface UserActivities {
  User createUser(UserData data);
}
