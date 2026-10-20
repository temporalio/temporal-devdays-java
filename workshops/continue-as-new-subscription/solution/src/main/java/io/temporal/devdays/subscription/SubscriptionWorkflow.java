package io.temporal.devdays.subscription;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** A subscription that bills every cycle until it reaches its last one. */
@WorkflowInterface
public interface SubscriptionWorkflow {
    @WorkflowMethod
    SubscriptionResult run(SubscriptionInput input);
}
