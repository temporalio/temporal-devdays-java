package io.temporal.devdays.shipment;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * The shipment Worker: hosts the Workflow and the packing Activity.
 * Run with: mvn -q compile exec:java@worker
 *
 * Ctrl+C is a crash, on purpose: the process exits without telling Temporal,
 * the way a lost node does, so the running attempt is left Started.
 */
public class WorkerApp {
    public static void main(String[] args) {
        String target = System.getenv().getOrDefault("TEMPORAL_ADDRESS", "127.0.0.1:7233");
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(Shared.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(ShipmentWorkflowImpl.class);
        worker.registerActivitiesImplementations(new ShipmentActivitiesImpl());

        factory.start();
        System.out.println("Shipment Worker polling " + Shared.TASK_QUEUE + ". Ctrl+C to crash it.");
    }
}
