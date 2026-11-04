package io.temporal.devdays.essentials.continueasnew;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface SubscriptionActivities {
  /** Charges one billing cycle and returns a receipt ID. */
  String chargeSubscription(String subscriptionId, int cycle);
}
