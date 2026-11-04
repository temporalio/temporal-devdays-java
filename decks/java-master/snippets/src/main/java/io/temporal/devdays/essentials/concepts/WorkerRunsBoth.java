package io.temporal.devdays.essentials.concepts;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * The Worker from 03-temporal-concepts.md "One Worker, both kinds of code". The runtime test hands
 * it the test server's client; in production the client points at your Temporal Service.
 */
public final class WorkerRunsBoth {
  private WorkerRunsBoth() {}

  public static WorkerFactory start(WorkflowClient client) {
    // slide: 03-temporal-concepts.md "One Worker, both kinds of code"
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker("transfers");
    worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
    worker.registerActivitiesImplementations(new BankActivitiesImpl());
    factory.start();
    // end slide
    return factory;
  }
}
