package io.temporal.devdays.essentials.claimcheck;

public class ReportActivitiesImpl implements ReportActivities {
  public static final int REPORT_BYTES = 1024 * 1024;

  @Override
  public byte[] buildReport() {
    return new byte[REPORT_BYTES];
  }
}
