package io.temporal.devdays.subscription;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import java.time.Duration;

/**
 * Every charge and every timer appends to one Event History, and that history
 * has a hard ceiling. Continue-As-New closes this run and starts a fresh one:
 * same Workflow Id, new Run Id, new short history.
 */
public class SubscriptionWorkflowImpl implements SubscriptionWorkflow {
    private final SubscriptionActivities activities = Workflow.newActivityStub(
            SubscriptionActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

    private final String subscriptionId;
    private final int maxCycles;
    private int cyclesCompleted;

    @WorkflowInit
    public SubscriptionWorkflowImpl(SubscriptionInput input) {
        this.subscriptionId = input.subscriptionId();
        this.cyclesCompleted = input.cyclesCompleted();
        this.maxCycles = input.maxCycles();
    }

    @Override
    public SubscriptionResult run(SubscriptionInput input) {
        while (maxCycles == 0 || cyclesCompleted < maxCycles) {
            activities.chargeSubscription(subscriptionId, cyclesCompleted + 1);
            cyclesCompleted++;
            Workflow.sleep(Shared.CYCLE);

            // TODO 1 start: when the Service suggests it, continue as new with what this run has done
            // TODO 1 end
        }
        return new SubscriptionResult(subscriptionId, cyclesCompleted);
    }
}
