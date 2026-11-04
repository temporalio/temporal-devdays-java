package io.temporal.devdays.essentials.serialization;

public class ClaimActivitiesImpl implements ClaimActivities {
  @Override
  public ClaimResult processClaim(Claim claim) {
    return new ClaimResult(claim.patientId(), claim.billedAmountCents());
  }
}
