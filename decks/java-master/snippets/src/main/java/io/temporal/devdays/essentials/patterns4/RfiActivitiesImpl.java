package io.temporal.devdays.essentials.patterns4;

import java.time.Duration;
import java.time.Instant;

public class RfiActivitiesImpl implements RfiActivities {
  private final EmailService emailService;
  private final ReportApi reportApi;

  public RfiActivitiesImpl(EmailService emailService, ReportApi reportApi) {
    this.emailService = emailService;
    this.reportApi = reportApi;
  }

  // part: 04-design-patterns.md "is worth testing in isolation" 1
  // RfiActivitiesImpl.java
  @Override
  public boolean validateEligibility(RfiRequest req) {
    boolean rfiIdValid = req.rfiId().matches("RFI-\\d{4}");
    boolean submittedRecently =
        req.submittedAt().isAfter(Instant.now().minus(Duration.ofDays(30)));
    boolean withinCostThreshold = req.estimatedCost() < 50_000;

    return rfiIdValid && submittedRecently && withinCostThreshold;
  }
  // end part

  // part: 04-design-patterns.md "can fail on its own" 1
  // RfiActivitiesImpl.java
  @Override
  public void notifyReviewer(RfiRequest req, String reviewer) {
    emailService.send(reviewer, "RFI " + req.rfiId() + " requires your review");
  }
  // end part

  // part: 04-design-patterns.md "takes significant time" 1
  // RfiActivitiesImpl.java
  @Override
  public void generateAnnualReport(RfiRequest req) {
    // compiles all RFIs for the year into a report
    reportApi.compileAnnual(req.projectId());
  }
  // end part
}
