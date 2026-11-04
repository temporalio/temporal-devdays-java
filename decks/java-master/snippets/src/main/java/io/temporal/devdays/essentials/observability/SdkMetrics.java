package io.temporal.devdays.essentials.observability;

import com.sun.net.httpserver.HttpServer;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** The SDK-metrics slide: a Prometheus /metrics endpoint for every client and Worker. */
public final class SdkMetrics {
  private SdkMetrics() {}

  /** The service-stub options that carry the metrics scope, and the server exporting them. */
  public record Exported(WorkflowServiceStubsOptions stubsOptions, HttpServer http) {}

  public static Exported start() throws IOException {
    // slide: 25-observability.md "Integration: SDK metrics in Java"
    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    Scope scope =
        new RootScopeBuilder()
            .reporter(new MicrometerClientStatsReporter(registry))
            .reportEvery(com.uber.m3.util.Duration.ofSeconds(1));
    WorkflowServiceStubsOptions stubsOptions =
        WorkflowServiceStubsOptions.newBuilder().setMetricsScope(scope).build();

    HttpServer http = HttpServer.create(new InetSocketAddress(9000), 0);
    http.createContext("/metrics", exchange -> {
      byte[] body = registry.scrape().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    http.start();
    // end slide
    return new Exported(stubsOptions, http);
  }
}
