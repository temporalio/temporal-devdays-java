package io.temporal.devdays.essentials.serialization;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.ByteArrayPayloadConverter;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.common.converter.NullPayloadConverter;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/** Builds the data converter from the slide and registers it on a Client and a Worker. */
public final class SerializationSetup {
  private SerializationSetup() {}

  /** The SDK's null and bytes converters, then ours in place of the default JSON one. */
  public static DataConverter dataConverter() {
    // part: 19-serialization.md "A custom payload converter" 2
    DataConverter converter =
        new DefaultDataConverter(
            new NullPayloadConverter(),
            new ByteArrayPayloadConverter(),
            new ZonedJsonConverter());
    // end part
    return converter;
  }

  public record Wired(WorkflowClient client, WorkerFactory factory, Worker worker) {}

  public static Wired withConverter(
      WorkflowServiceStubs service, DataConverter converter, String taskQueue) {
    // slide: 19-serialization.md "Register it on both sides"
    WorkflowClientOptions options =
        WorkflowClientOptions.newBuilder().setDataConverter(converter).build();

    WorkflowClient client = WorkflowClient.newInstance(service, options);
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(taskQueue);
    // end slide
    return new Wired(client, factory, worker);
  }
}
