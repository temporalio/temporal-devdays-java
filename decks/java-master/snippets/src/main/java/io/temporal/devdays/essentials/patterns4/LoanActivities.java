package io.temporal.devdays.essentials.patterns4;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface LoanActivities {
  CreditCheck runCreditCheck(Applicant applicant);

  FraudCheck checkFraud(Applicant applicant, CreditCheck creditCheck);

  Decision makeDecision(Applicant applicant, CreditCheck creditCheck, FraudCheck fraud);
}
