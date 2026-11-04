package io.temporal.devdays.essentials.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The SDK-metrics slide: the service stubs carry the metrics scope, and /metrics on port 9000
 * serves the {@code temporal_}-prefixed names the SDK metric slides use.
 */
class ObservabilityMetricsTest {

  private static final List<String> NAMES =
      List.of(
          "temporal_worker_task_slots_available",
          "temporal_num_pollers",
          "temporal_sticky_cache_size",
          "temporal_activity_execution_latency",
          "temporal_workflow_task_execution_latency",
          "temporal_workflow_task_replay_latency",
          "temporal_workflow_task_schedule_to_start_latency",
          "temporal_activity_schedule_to_start_latency",
          "temporal_long_request_latency");

  @Test
  void aWorkerOnTheMetricsStubsExportsTheSdkMetrics() throws Exception {
    SdkMetrics.Exported exported = SdkMetrics.start();
    TestWorkflowEnvironment env =
        TestWorkflowEnvironment.newInstance(
            TestEnvironmentOptions.newBuilder()
                .setWorkflowServiceStubsOptions(exported.stubsOptions())
                .build());
    try {
      Worker worker = env.newWorker("observability-metrics");
      worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
      worker.registerActivitiesImplementations(new OrderActivitiesImpl());
      env.start();

      String receipt =
          env.getWorkflowClient()
              .newWorkflowStub(
                  OrderWorkflow.class,
                  WorkflowOptions.newBuilder()
                      .setTaskQueue("observability-metrics")
                      .setWorkflowId(UUID.randomUUID().toString())
                      .build())
              .processOrder("A-2");
      assertEquals("receipt-A-2", receipt);

      // The scope reports every second; scrape until every name has arrived.
      HttpClient http = HttpClient.newHttpClient();
      String metrics = "";
      for (int i = 0; i < 50 && !NAMES.stream().allMatch(metrics::contains); i++) {
        Thread.sleep(200);
        metrics =
            http.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:9000/metrics")).build(),
                    HttpResponse.BodyHandlers.ofString())
                .body();
      }

      for (String name : NAMES) {
        assertTrue(
            metrics.contains(name),
            "SdkMetrics.java (Integration: SDK metrics in Java): /metrics has no " + name);
      }
      assertTrue(
          metrics.contains("temporal_activity_execution_latency_seconds"),
          "the notes claim Micrometer's Prometheus registry adds _seconds to timer names");
      for (String type : List.of("WorkflowWorker", "ActivityWorker")) {
        assertTrue(
            Pattern.compile(
                    "temporal_worker_task_slots_available\\{[^}]*worker_type=\"" + type + "\"")
                .matcher(metrics)
                .find(),
            "the 'Compute settings to metrics' slide claims slots available is tagged"
                + " worker_type=\"" + type + "\"");
      }
    } finally {
      env.close();
      exported.http().stop(0);
    }
  }
}
