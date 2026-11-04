package io.temporal.devdays.essentials.encryption;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.CodecDataConverter;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import java.util.List;
import javax.crypto.SecretKey;

/** One codec, on the client the Worker is built from. Workflow code never changes. */
public final class EncryptionSetup {
  private EncryptionSetup() {}

  public record Wired(WorkflowClient client, WorkerFactory factory, Worker worker) {}

  public static Wired withCodec(WorkflowServiceStubs service, SecretKey key, String taskQueue) {
    // slide: 20-encryption.md "Same codec, Client and Worker"
    DataConverter converter =
        new CodecDataConverter(
            DefaultDataConverter.newDefaultInstance(),
            List.of(new EncryptionCodec(key)));

    WorkflowClient client =
        WorkflowClient.newInstance(
            service, WorkflowClientOptions.newBuilder().setDataConverter(converter).build());
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(taskQueue);
    // end slide
    return new Wired(client, factory, worker);
  }
}
