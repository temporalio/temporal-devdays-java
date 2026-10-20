package io.temporal.devdays.subscription;

import io.temporal.activity.ActivityInterface;

/** The one Activity a subscription runs: charge one billing cycle. */
@ActivityInterface
public interface SubscriptionActivities {
    /** Charges one billing cycle and returns a receipt id. */
    String chargeSubscription(String subscriptionId, int cycle);
}
