package io.temporal.devdays.essentials.antipatterns;

import io.temporal.failure.ApplicationFailure;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The payment network the polling slide waits on. A wire settles on the Nth status check. */
public class WireActivitiesImpl implements WireActivities {
  /** Every status check, in order. */
  public final List<String> settlementChecks = new CopyOnWriteArrayList<>();

  private final int settlesOnCheck;

  public WireActivitiesImpl(int settlesOnCheck) {
    this.settlesOnCheck = settlesOnCheck;
  }

  @Override
  public String checkSettlement(String wireId) {
    settlementChecks.add(wireId);
    return settlementChecks.size() >= settlesOnCheck ? "SETTLED" : "PENDING";
  }

  // The ✅ half of the polling slide: one check per attempt, and a retryable failure when the
  // wire has not settled, so the Retry Policy does the waiting.
  @Override
  public void awaitSettlement(String wireId) {
    if (!checkSettlement(wireId).equals("SETTLED")) {
      throw ApplicationFailure.newFailure("Wire " + wireId + " not settled yet", "WirePending");
    }
  }
}
