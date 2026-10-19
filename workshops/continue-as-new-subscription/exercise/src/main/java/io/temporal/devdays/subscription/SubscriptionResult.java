package io.temporal.devdays.subscription;

/** What the last run of the chain returns. */
public record SubscriptionResult(String subscriptionId, int totalCycles) {}
