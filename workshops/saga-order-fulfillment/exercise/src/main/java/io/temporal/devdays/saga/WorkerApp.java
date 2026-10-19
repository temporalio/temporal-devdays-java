package io.temporal.devdays.saga;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * The fulfillment Worker: hosts the Saga Workflow and all five Activities.
 * Run with: mvn -q compile exec:java@worker (run-worker in the lab).
 *
 * The [ledger] lines print here. That is where you see which steps and undos
 * ran, and in which order.
 */
public class WorkerApp {
    public static void main(String[] args) {
        String target = System.getenv().getOrDefault("TEMPORAL_ADDRESS", "127.0.0.1:7233");
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(Shared.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(OrderFulfillmentWorkflowImpl.class);
        worker.registerActivitiesImplementations(new OrderActivitiesImpl());

        factory.start();
        System.out.println("Fulfillment Worker polling " + Shared.TASK_QUEUE);
    }
}
