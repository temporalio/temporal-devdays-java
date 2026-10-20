package io.temporal.devdays.subscription;

public class SubscriptionActivitiesImpl implements SubscriptionActivities {
    @Override
    public String chargeSubscription(String subscriptionId, int cycle) {
        String receipt = "rcpt-" + subscriptionId + "-" + cycle;
        System.out.println("[activity] charged " + subscriptionId + " cycle " + cycle + " -> " + receipt);
        return receipt;
    }
}
