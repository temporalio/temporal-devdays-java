package io.temporal.devdays.essentials.patterns4;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface RfiActivities {
  boolean validateEligibility(RfiRequest req);

  void notifyReviewer(RfiRequest req, String reviewer);

  void generateAnnualReport(RfiRequest req);
}
