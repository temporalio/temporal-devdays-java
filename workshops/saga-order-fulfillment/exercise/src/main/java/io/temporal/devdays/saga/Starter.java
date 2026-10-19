package io.temporal.devdays.saga;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.util.Locale;
import java.util.UUID;

/**
 * Places one order. Watch the WORKER terminal for the [ledger] lines: that is
 * where you see whether the undos ran, and in which order.
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

        OrderFulfillmentWorkflow workflow = client.newWorkflowStub(
                OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(Shared.TASK_QUEUE)
                        .setWorkflowId("fulfillment-" + order.orderId())
                        .build());

        System.out.println("\nFulfilling " + order.orderId() + "...");
        System.out.println("Watch the Worker terminal for [ledger] lines.\n");
        try {
            OrderResult result = workflow.fulfill(order);
            System.out.println("Order completed. Tracking number " + result.trackingNumber());
        } catch (WorkflowFailedException e) {
            // A failed order is a business outcome here, not a crash of the starter.
            System.out.println("Order failed: " + Shared.rootCause(e));
            System.out.println("\nNow read the ledger in the Worker terminal. A correctly");
            System.out.println("compensated order ends with the undos present, newest first:");
            System.out.println("  refund before release.");
        }
        System.exit(0);
    }
}
