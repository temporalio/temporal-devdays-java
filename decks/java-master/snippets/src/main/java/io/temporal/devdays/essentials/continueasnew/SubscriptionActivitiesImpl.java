package io.temporal.devdays.essentials.continueasnew;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SubscriptionActivitiesImpl implements SubscriptionActivities {
  /** Every cycle charged, in order, across all runs. */
  public final List<Integer> charges = new CopyOnWriteArrayList<>();

  @Override
  public String chargeSubscription(String subscriptionId, int cycle) {
    charges.add(cycle);
    return subscriptionId + "-receipt-" + cycle;
  }
}
