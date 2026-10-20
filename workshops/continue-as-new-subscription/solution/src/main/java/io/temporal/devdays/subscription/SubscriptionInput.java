package io.temporal.devdays.subscription;

/**
 * The input of every run in a Continue-As-New chain. Whatever a run has done
 * that is not in the input it hands to the next run is gone.
 *
 * @param subscriptionId  the subscription being billed
 * @param cyclesCompleted billing cycles already charged by earlier runs
 * @param maxCycles       stop after this many billing cycles; 0 means bill forever
 */
public record SubscriptionInput(String subscriptionId, int cyclesCompleted, int maxCycles) {
    /** A new subscription that has billed nothing yet. */
    public static SubscriptionInput start(String subscriptionId, int maxCycles) {
        return new SubscriptionInput(subscriptionId, 0, maxCycles);
    }
}
