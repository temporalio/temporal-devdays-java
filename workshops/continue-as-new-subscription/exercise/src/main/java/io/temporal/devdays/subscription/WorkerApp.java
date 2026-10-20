package io.temporal.devdays.subscription;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * The subscription Worker: hosts the Workflow and its one Activity. Run with
 * mvn -q compile exec:java@worker. The [activity] lines print here, one per
 * billed cycle.
 */
public class WorkerApp {
    public static void main(String[] args) {
        String target = System.getenv().getOrDefault("TEMPORAL_ADDRESS", "127.0.0.1:7233");
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(Shared.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(SubscriptionWorkflowImpl.class);
        worker.registerActivitiesImplementations(new SubscriptionActivitiesImpl());

        factory.start();
        System.out.println("Subscription Worker polling " + Shared.TASK_QUEUE + ". Ctrl+C to stop.");
    }
}
