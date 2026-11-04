package io.temporal.devdays.essentials.patterns4;

/** Not on a slide: stands in for the claims system. */
public class ClaimActivitiesImpl implements ClaimActivities {
  @Override
  public ApprovalStatus autoApprove(Claim claim) {
    return ApprovalStatus.APPROVED;
  }

  @Override
  public Approval getApproval(Claim claim) {
    return new Approval(claim.claimId(), claim.amount() <= 10_000);
  }
}
