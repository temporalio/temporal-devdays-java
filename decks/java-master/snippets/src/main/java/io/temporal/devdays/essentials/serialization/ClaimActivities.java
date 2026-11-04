package io.temporal.devdays.essentials.serialization;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface ClaimActivities {
  ClaimResult processClaim(Claim claim);
}
