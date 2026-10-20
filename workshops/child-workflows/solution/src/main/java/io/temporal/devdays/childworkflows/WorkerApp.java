package io.temporal.devdays.childworkflows;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/** Hosts every parent and every child Workflow. Run with: mvn -q compile exec:java@worker */
public class WorkerApp {
    public static void main(String[] args) {
        String target = System.getenv().getOrDefault("TEMPORAL_ADDRESS", "127.0.0.1:7233");
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        // A parent and its children can share one Worker and one Task Queue:
        // a child runs on its parent's Task Queue unless told otherwise.
        Worker worker = factory.newWorker(Shared.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(
                OrderWorkflowImpl.class,
                ShipmentWorkflowImpl.class,
                ChainWorkflowImpl.class,
                PackWorkflowImpl.class,
                PackItemWorkflowImpl.class);

        factory.start();
        System.out.println("Worker polling " + Shared.TASK_QUEUE + ". Ctrl+C to stop.");
    }
}
