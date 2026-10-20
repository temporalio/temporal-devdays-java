package io.temporal.devdays.shipment;

import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.CanceledFailure;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/**
 * Packs one shipment and waits for the result.
 * Run with: mvn -q compile exec:java@starter [-Dexec.args=tag]
 *
 *   (no argument)   shipment-demo
 *   tag             shipment-tag
 *
 * Starting again with the same id replaces the old shipment, so a run left
 * retrying never blocks the next experiment.
 */
public class Starter {
    public static void main(String[] args) {
        String workflowId = "shipment-" + (args.length > 0 ? args[0] : "demo");
        String target = System.getenv().getOrDefault("TEMPORAL_ADDRESS", "127.0.0.1:7233");
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        WorkflowClient client = WorkflowClient.newInstance(service);

        ShipmentWorkflow workflow = client.newWorkflowStub(
                ShipmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(Shared.TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        .setWorkflowIdConflictPolicy(
                                WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_TERMINATE_EXISTING)
                        .build());

        System.out.println("Packing " + workflowId + ". " + Shared.BATCH_SIZE + " items.");
        System.out.println("Watch the Worker terminal count them.");

        ShipmentResult result;
        try {
            result = workflow.run(new ShipmentInput(workflowId, Shared.BATCH_SIZE));
        } catch (WorkflowFailedException e) {
            if (e.getCause() instanceof CanceledFailure) {
                System.out.println();
                System.out.println(workflowId + " was cancelled. It ended Canceled, not Completed.");
                System.exit(0);
            }
            throw e;
        }

        System.out.println();
        System.out.println("Packed " + result.packed() + " items, finished on attempt "
                + result.finishedOnAttempt() + ".");
        if (result.finishedOnAttempt() > 1) {
            System.out.println("An earlier attempt was interrupted. This one finished the job.");
        }
        System.exit(0);
    }
}
