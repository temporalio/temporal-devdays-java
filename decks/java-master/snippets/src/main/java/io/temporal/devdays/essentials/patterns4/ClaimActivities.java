package io.temporal.devdays.essentials.patterns4;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface ClaimActivities {
  ApprovalStatus autoApprove(Claim claim);

  Approval getApproval(Claim claim);
}
