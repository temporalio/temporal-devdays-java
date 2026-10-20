package io.temporal.devdays.greeting;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/** Starts one Workflow and prints its result. Run with: mvn -q compile exec:java@starter */
public class Starter {
    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "Temporal";
        String target = System.getenv().getOrDefault("TEMPORAL_ADDRESS", "127.0.0.1:7233");
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        WorkflowClient client = WorkflowClient.newInstance(service);

        GreetingWorkflow workflow = client.newWorkflowStub(
                GreetingWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(Shared.TASK_QUEUE)
                        .setWorkflowId("greeting-" + System.currentTimeMillis())
                        .build());
        System.out.println(workflow.greet(name));
        System.exit(0);
    }
}
