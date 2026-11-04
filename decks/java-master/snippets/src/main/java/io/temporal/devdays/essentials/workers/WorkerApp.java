package io.temporal.devdays.essentials.workers;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

// The Worker from 07-workers.md. Compiled, never run: main would connect to
// localhost:7233 and poll forever. WorkerAppTest builds the same Worker
// against the test server instead.
//
// slide: 07-workers.md "Java Workers"
// WorkerApp.java
public class WorkerApp {
  public static void main(String[] args) {
    WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget("localhost:7233").build());
    WorkflowClient client = WorkflowClient.newInstance(service);
    WorkerFactory factory = WorkerFactory.newInstance(client);

    Worker worker = factory.newWorker("transfers");
    worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
    worker.registerActivitiesImplementations(new BankActivitiesImpl());

    factory.start();
  }
}
// end slide
