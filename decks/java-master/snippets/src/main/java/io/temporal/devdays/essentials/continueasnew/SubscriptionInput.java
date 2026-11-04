package io.temporal.devdays.essentials.continueasnew;

/**
 * The input of every run in the Continue-As-New chain, and so the state carried forward.
 *
 * @param subscriptionId the subscription being billed
 * @param cyclesCompleted billing cycles already charged, by this run and every run before it
 */
public record SubscriptionInput(String subscriptionId, int cyclesCompleted) {}
