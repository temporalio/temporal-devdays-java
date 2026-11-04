package io.temporal.devdays.essentials.patterns4;

/** Not on a slide: in-memory stand-ins for the credit bureau and the fraud service. */
public class LoanActivitiesImpl implements LoanActivities {
  @Override
  public CreditCheck runCreditCheck(Applicant applicant) {
    return new CreditCheck(applicant.income() > 50_000 ? 720 : 580);
  }

  @Override
  public FraudCheck checkFraud(Applicant applicant, CreditCheck creditCheck) {
    return new FraudCheck(creditCheck.score() < 600);
  }

  @Override
  public Decision makeDecision(Applicant applicant, CreditCheck creditCheck, FraudCheck fraud) {
    return creditCheck.score() >= 650 && !fraud.flagged() ? Decision.APPROVED : Decision.DECLINED;
  }
}
