package io.temporal.devdays.subscription;

import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.TerminatedFailure;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/**
 * Bills one subscription for 30 cycles and waits for it to finish.
 *
 *   mvn -q compile exec:java@starter                      subscription-demo
 *   mvn -q compile exec:java@starter -Dexec.args=<tag>    subscription-<tag>
 *
 * Starting again with the same id replaces the old subscription.
 */
public class Starter {
    static final int TARGET_CYCLES = 30;

    public static void main(String[] args) {
        String workflowId = "subscription-" + (args.length > 0 && !args[0].isBlank() ? args[0] : "demo");
        String target = System.getenv().getOrDefault("TEMPORAL_ADDRESS", "127.0.0.1:7233");
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        WorkflowClient client = WorkflowClient.newInstance(service);

        SubscriptionWorkflow workflow = client.newWorkflowStub(
                SubscriptionWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(Shared.TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_TERMINATE_EXISTING)
                        .build());
        WorkflowClient.start(workflow::run, SubscriptionInput.start(workflowId, TARGET_CYCLES));
        System.out.println("Billing " + workflowId + " for " + TARGET_CYCLES + " cycles. Waiting for it to finish...");

        try {
            // Follows the chain: waits for the last run, however many rollovers it takes.
            SubscriptionResult result = WorkflowStub.fromTyped(workflow).getResult(SubscriptionResult.class);
            System.out.println("\nDone. Billed " + result.totalCycles() + " cycles.");
        } catch (WorkflowFailedException e) {
            if (e.getCause() instanceof TerminatedFailure terminated) {
                System.out.println("\n" + workflowId + " was terminated by the Temporal Service: "
                        + terminated.getOriginalMessage());
            } else {
                throw e;
            }
        }
        System.exit(0);
    }
}
