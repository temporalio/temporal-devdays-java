package io.temporal.devdays.saga;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.util.Locale;
import java.util.UUID;

/**
 * Places one order, waits for its Workflow to close, then reads the Workflow's
 * Event History and prints what Temporal recorded: the steps and undos that
 * completed, and any undo a landed step is still owed.
 *
 *   run-starter                      a clean order that succeeds
 *   run-starter --fail-reserve       inventory fails before anything lands
 *   run-starter --fail-dispatch      the courier fails after two steps landed
 *   run-starter --charge-times-out   the charge lands, then reports failure
 */
public class Starter {
    private static final String USAGE =
            "usage: run-starter [--fail-reserve] [--fail-dispatch] [--charge-times-out]";

    public static void main(String[] args) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        Order order = Order.widget("ORD-" + id);
        for (String arg : args) {
            switch (arg) {
                case "--fail-reserve" -> order = order.withFailReserve();
                case "--fail-dispatch" -> order = order.withFailDispatch();
                case "--charge-times-out" -> order = order.withChargeFailsAfterEffect();
                default -> {
                    System.err.println("Unknown option " + arg + "\n" + USAGE);
                    System.exit(2);
                }
            }
        }

        String target = System.getenv().getOrDefault("TEMPORAL_ADDRESS", "127.0.0.1:7233");
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        WorkflowClient client = WorkflowClient.newInstance(service);

        String workflowId = "fulfillment-" + order.orderId();
        OrderFulfillmentWorkflow workflow = client.newWorkflowStub(
                OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(Shared.TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        .build());

        System.out.println("\nFulfilling " + order.orderId() + "...\n");
        try {
            OrderResult result = workflow.fulfill(order);
            System.out.println("Order completed. Tracking number " + result.trackingNumber());
        } catch (WorkflowFailedException e) {
            // A failed order is a business outcome here, not a crash of the starter.
            System.out.println("Order failed: " + Shared.rootCause(e));
            System.out.println("The Workflow ended Failed. That is expected: the order did not go through.");
        }

        // Read back what Temporal recorded, not what any terminal printed.
        HistorySummary summary = HistorySummary.of(client.fetchHistory(workflowId), order);
        System.out.println("\nEvent History of " + workflowId + ":");
        summary.lines().forEach(System.out::println);
        System.exit(0);
    }
}
