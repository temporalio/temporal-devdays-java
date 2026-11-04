package io.temporal.devdays.essentials.claimcheck;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.payload.storage.ExternalStorage;
import io.temporal.payload.storage.StorageDriver;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/** External Storage on the client options; the Worker built from that client inherits it. */
public final class ExternalStorageSetup {
  private ExternalStorageSetup() {}

  public record Wired(WorkflowClient client, WorkerFactory factory, Worker worker) {}

  public static Wired withExternalStorage(
      WorkflowServiceStubs service, StorageDriver driver, String taskQueue) {
    // slide: 21-claim-check.md "Or let the SDK do it"
    ExternalStorage storage =
        ExternalStorage.newBuilder()
            .setDriver(driver)
            .setPayloadSizeThreshold(256 * 1024)
            .build();

    WorkflowClient client =
        WorkflowClient.newInstance(
            service, WorkflowClientOptions.newBuilder().setExternalStorage(storage).build());
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(taskQueue);
    // end slide
    return new Wired(client, factory, worker);
  }
}
