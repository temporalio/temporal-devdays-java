package io.temporal.devdays.essentials.resumable;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * The Worker for the Resumable Activity demo in 17b-patterns-and-anti-patterns.md. Start a dev
 * server ({@code temporal server start-dev}), then from decks/java-master/snippets:
 *
 * <pre>
 * mvn -q compile exec:java -Dexec.mainClass=io.temporal.devdays.essentials.resumable.ResumableDemo
 * </pre>
 *
 * The commands to drive it are in that demo slide's speaker notes.
 */
public final class ResumableDemo {
  public static final String TASK_QUEUE = "resumable-demo";

  private ResumableDemo() {}

  public static void main(String[] args) {
    WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
    WorkflowClient client = WorkflowClient.newInstance(service);
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
    worker.registerActivitiesImplementations(new TransferActivitiesImpl());
    factory.start();
    System.out.println(
        "Worker polling "
            + TASK_QUEUE
            + ". Accounts that exist: "
            + String.join(", ", TransferActivitiesImpl.KNOWN_ACCOUNTS.stream().sorted().toList()));
  }
}
