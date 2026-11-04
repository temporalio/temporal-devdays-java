---
layout: section
---

<ZiggyHero scene="observe">

# Observability

Knowing it's healthy before someone tells you it isn't.

</ZiggyHero>

<!--
Source: PPT slides 168-186.

Most of this segment is Temporal Cloud and SDK metrics, which are the same
whatever language your Workers are in. The Java-specific parts are at the end:
turning the metrics exporter on with a Micrometer registry on the service
stubs, and logging from Workflows and Activities.
-->

---
layout: default
---

# Observability objectives

- Ensuring high availability and maintaining application health
- Diagnosing and troubleshooting performance bottlenecks
- Detecting failures and errors
- Optimizing Worker performance
- Making informed operational decisions

<!--
Source: PPT slide 169.
-->

---
layout: default
---

# SDK vs Cloud metrics

<div class="cols-2">
<div>
<p class="col-eyebrow">SDK metrics</p>

**Emitted by your Clients and Workers**

- Performance of your code on your Workers
- Error rates at the Client and Worker level
- Cache use within Workers
- Resource use and availability on Worker hosts

</div>
<div>
<p class="col-eyebrow">Cloud metrics</p>

**Emitted by Temporal Cloud**

- Your workload, and Worker fleet health against Task Queues
- Service request latency and counts
- Sync vs async Task matching rates
- Service availability and error rates
- Throttling and rate limits imposed by the server

</div>
</div>

<!--
Source: PPT slide 170.

The closing line from the original: SDK and Cloud metrics together give a
holistic view of your Temporal application. Effective monitoring usually means
correlating the two to diagnose performance issues. A slow Workflow is either
slow in your code (SDK metrics) or slow getting to your code (Cloud metrics),
and you need both to tell which.
-->

---
layout: section
---

<ZiggyHero scene="gauge">

# Key SDK metrics

</ZiggyHero>

<!--
Source: PPT slide 171.

The Java SDK emits every metric with a `temporal_` prefix (the constant
`MetricsType.TEMPORAL_METRICS_PREFIX`), so `worker_task_slots_available` is
`temporal_worker_task_slots_available` in your Prometheus. The slides use the
full names. One thing to warn the room about: through Micrometer's Prometheus
registry, timers gain a unit suffix, so `temporal_activity_execution_latency`
is scraped as `temporal_activity_execution_latency_seconds_*`. Search for the
name, not the exact series. `ObservabilityMetricsTest` checks the slot,
poller, cache, latency and long-request names on these slides against a real
scrape. (Counters such as the eviction count appear only once they count
something.)
-->

---
layout: default
---

# Worker throughput and Task execution

- **Worker slot availability** · `temporal_worker_task_slots_available`
  - **The goal:** always have headroom. If this sits at zero, the Worker is
    saturated and cannot accept new Tasks, and processing is delayed
- **Activity latency** · `temporal_activity_execution_latency`
  - **The insight:** time spent inside your Activity code
  - **Action:** persistent spikes point to inefficient code, slow database
    queries, or a slow downstream API

<!--
Source: PPT slide 172.
-->

---
layout: default
---

# Workflow performance

**The 10-second rule:** by default a Workflow Task must complete within 10
seconds. If it doesn't, check two metrics:

- `temporal_workflow_task_replay_latency`: how long it takes to rebuild state from history
- `temporal_workflow_task_execution_latency`: how long it takes to run new decisions

If these are high, your history is probably too large. **Use Continue-As-New**
to break it up and cut what the Worker has to process.

<!--
Source: PPT slide 173.

In Java there are two more common causes of high execution latency.

- **Workflow code that holds its thread.** Each Workflow runs on its own
  Workflow thread, and the SDK hands control between those threads one at a
  time. Code that does CPU work, blocking IO, `Thread.sleep` or a
  `synchronized` wait without going back to the SDK holds up the Task. After 1
  second without yielding (`WorkerOptions.setDefaultDeadlockDetectionTimeout`,
  default 1000 ms) the Worker fails the Workflow Task with
  `[TMPRL1101] Potential deadlock detected`. The Workflow stays open and the
  Task is retried, so it fails again until you fix the code. `TuningTest`
  proves it.
- **Running out of Workflow threads.** Workflow threads come from a pool
  shared by every Worker in the `WorkerFactory`, capped at 600 by
  `WorkerFactoryOptions.setMaxWorkflowThreadCount`. Cached Workflows keep their
  threads, so when the pool runs dry the Worker evicts cached Workflows to free
  threads, and they pay a full replay next time. Watch
  `temporal_workflow_active_thread_count` against that cap.
-->

---
layout: default
---

# Cache optimization and Worker memory

**The sticky cache:** your Worker caches Workflow state so it doesn't have to
download the whole Event History from Temporal for every Task.

- `temporal_sticky_cache_total_forced_eviction`: should stay stable and low
- `temporal_sticky_cache_size`: watch it to right-size the Worker's memory

<!--
Source: PPT slide 174.

In Java the cache size is `WorkerFactoryOptions.setWorkflowCacheSize`, default
600, shared by every Worker the factory creates. In practice the thread pool
often caps it first: each cached Workflow keeps its Workflow threads parked,
and the pool (`setMaxWorkflowThreadCount`, default 600) is shared too. When a
Task needs a thread and none is free, the Worker evicts a cached Workflow,
counted in `temporal_sticky_cache_thread_forced_eviction`. Forced evictions
climbing means the cache is too small for the number of open Workflows this
Worker handles: each eviction costs a full replay next time.
-->

---
layout: default
---

# Poller health and connectivity

- `temporal_num_pollers`: total active long-polls from your Worker to Temporal
- `temporal_long_request_latency`: a long-poll should stay open for nearly a
  minute while it waits for work. Low values here often mean your network is
  cutting connections early
- `temporal_cloud_v1_poll_timeout_count`: on an idle Task Queue, timeouts are
  expected and healthy. Under steady load, poll success rate (success over
  success plus timeouts) should be above 90%

<!--
Source: PPT slide 175.

The counterintuitive one is the last: on an idle Task Queue, almost every poll
should time out empty. That is a healthy connection doing its job. Under load,
the docs target a poll success rate above 90% (above 95% for high volume); a
low success rate under load often means more pollers than the work needs.

The original slide used the `temporal_cloud_v0_*` name. The v0 (PromQL)
metrics were retired when that endpoint shut down on 2026-10-05; every Cloud
metric in this segment uses the v1 OpenMetrics name.
-->

---
layout: default
---

# The gold-standard metrics

- **Schedule-to-start latency** · `temporal_activity_schedule_to_start_latency`,
  `temporal_workflow_task_schedule_to_start_latency`
  - How long a Task sits in the Task Queue before a Worker picks it up. Keep p90
    under 700ms. If it climbs, **scale your Workers**
- **Sync match rate** (calculated): ideally above 95%. The Task was handed
  straight to a waiting poller without ever touching the database
- **Service latency and poll success** (calculated): the handshake between your
  network and Temporal Cloud is seamless

<!--
Source: PPT slide 176.

If you only alert on one thing, alert on schedule-to-start. It is the single
best signal that you do not have enough Workers.
-->

---
layout: section
---

<ZiggyHero scene="cloud">

# Cloud-side observability

</ZiggyHero>

<!--
Source: PPT slide 177.
-->

---
layout: default
---

# Cloud metrics: resource exhaustion

`temporal_cloud_v1_resource_exhausted_error_count`

- **What it measures:** requests rejected because one resource (a Namespace,
  Task Queue, or Workflow ID) got a burst larger than it could absorb
- **Why it matters:** a hot resource. SDKs retry these, so progress is rarely
  hit, but persistent non-zero values are unexpected
- **Not the same as throttling:** for APS/RPS limits, watch
  `temporal_cloud_v1_total_action_throttled_count` and
  `temporal_cloud_v1_service_request_throttled_count`
- **Actions:** use the `operation` label to find the hot RPC, smooth the burst

<!--
Source: PPT slide 178.

The original slide described this metric as APS/RPS throttling. The OpenMetrics
reference says the opposite: it counts per-resource bursts and "does not include
throttling due to Namespace limits". A common example: starting the same
Workflow ID more than once per second.
-->

---
layout: default
---

# Cloud metrics: frontend service errors

`temporal_cloud_v1_service_error_count`

- **What it measures:** errors reported by the Temporal Cloud frontend service API
- **Why it matters:** indicates communication issues between your Clients and Temporal Cloud
- **Signs of trouble:** success rate below 99.9%
- **Actions:** check the metric's `operation` and `status_code` tags to find the
  type of error, and verify network connectivity between your Clients and
  Temporal Cloud

<!--
Source: PPT slide 179. The original used the v0 name
`temporal_cloud_v0_frontend_service_error_count`; its v1 equivalent is
`temporal_cloud_v1_service_error_count`.
-->

---
layout: section
---

<ZiggyHero scene="plug">

# Observability: integrations

</ZiggyHero>

<!--
Source: PPT slide 181.
-->

---
layout: default
---

# Metrics endpoints

<div class="cols-2">
<div>
<p class="col-eyebrow">OpenMetrics endpoint</p>

A Prometheus-compatible, **scrapable** endpoint. Ideal for pull-based tools
(Grafana Cloud, Prometheus, and so on).

</div>
<div>
<p class="col-eyebrow">PromQL endpoint · retired</p>

The older Prometheus **query API** endpoint. Shut down on 2026-10-05: anything
still pointing at it gets no data. Move it to OpenMetrics.

</div>
</div>

<!--
Source: PPT slide 182.

OpenMetrics exposes Cloud metrics for every Namespace in your account, as
pre-computed rates at `https://metrics.temporal.io/v1/metrics`, authenticated
with a Metrics Read-Only API key. SDK metrics are separate: you turn them on in
your Worker code (later in this segment).

The PromQL column is here for rooms with dashboards that went quiet in
October 2026: that is why. Drop it once nobody asks.
-->

---
layout: default
---

# Integration: Grafana Cloud

<div class="cols-2">
<div>
<p class="col-eyebrow">What you get</p>

- A pre-built, always-updated dashboard for Workflow health, Task Queue depth, and usage
- Dimensions: Namespace, Task Queue, Workflow Type, operation
- Adaptive Telemetry for cost control, and Grafana Assistant for natural-language queries

</div>
<div>
<p class="col-eyebrow">Setup</p>

1. Create a service account API key with the Metrics Read-Only role
2. In Grafana Cloud, open Connections and find the Temporal Cloud tile
3. Configure a scrape job with the API key
4. No mTLS certificates or self-hosted Prometheus needed

</div>
</div>

<!--
Source: PPT slide 183.
-->

---
layout: default
---

# Integration: Datadog

<div class="cols-2">
<div>
<p class="col-eyebrow">What you get</p>

- A ready-made dashboard for health, efficiency, and performance
- Built-in monitors: p99 latency, Workflow failures, replication lag
- Standard, not custom, metrics: lower Datadog billing

</div>
<div>
<p class="col-eyebrow">Setup</p>

1. Create a service account with the Metrics Read-Only role, and an API key
2. In Datadog, install the **Temporal Cloud - OpenMetrics** integration
3. Add your account and paste the API key
4. No certificates needed

</div>
</div>

<!--
Source: PPT slide 184. The original described the deprecated integration
(root CA upload, certificates). The current one uses an API key and the
`temporal.cloud.v1_*` metric prefix. The old tile stopped receiving data when the
PromQL endpoint shut down on 2026-10-05.
-->

---
layout: default
---

# Integration: self-hosted Prometheus

<div class="cols-2">
<div>
<p class="col-eyebrow">What you get</p>

- Every Cloud metric, scraped from the OpenMetrics endpoint
- Temporal's OpenMetrics Grafana dashboard, on GitHub
- Feeds an OpenTelemetry Collector or your own alerting

</div>
<div>
<p class="col-eyebrow">Setup</p>

1. Create a Metrics Read-Only service account and API key
2. Add a Prometheus scrape job for the OpenMetrics endpoint, with the key
   as a Bearer token
3. Import the OpenMetrics dashboard into Grafana

</div>
</div>

<!--
Source: PPT slide 185. The original title was "self-hosted Prometheus and
Grafana"; Grafana is the dashboard on top of the Prometheus data source.

The original setup (mTLS certificates, a CA upload, PromQL as a Grafana data
source) was for the deprecated PromQL endpoint. The current path is a scrape
job against `https://metrics.temporal.io/v1/metrics` with a Bearer API key,
and the dashboard is `temporal_cloud_openmetrics.json` in temporalio/dashboards.
The migration guide at
docs.temporal.io/cloud/metrics/openmetrics/migration-guide has the full
`scrape_configs` example. OpenMetrics v1 values are pre-computed rates, so use
`sum()`, not `rate()` or `increase()`, in queries.
-->

---
layout: code-walkthrough
heading: "Integration: SDK metrics in Java"
notes:
  - The SDK reports to a tally <code>Scope</code>. <code>MicrometerClientStatsReporter</code> hands every metric to a Micrometer Prometheus registry
  - Set the scope on the <strong>service stubs</strong>, the connection. Every client and Worker built on them reports through it
  - Serve the registry at <code>/metrics</code> on port 9000, from the Worker Process
---

::code::

```java {all|1-5|6-7|9-17}
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
```

<!--
Source: PPT slide 186.

What you get: schedule-to-start latency, poll success, sync match; Task slots
available, sticky cache size and evictions; request latency and failures; and
Activity Execution and Workflow Execution metrics. Everything on the SDK
metrics slides, with the same `temporal_` names (timers with a `_seconds`
suffix, as the Key SDK metrics notes say).

- **Build 1 - the scope.** The Java SDK records metrics into a tally `Scope`
  (`com.uber.m3.tally`). `MicrometerClientStatsReporter` ships with
  `temporal-sdk` and forwards everything to any Micrometer `MeterRegistry`; the
  Prometheus one needs `io.micrometer:micrometer-registry-prometheus` on the
  classpath. `reportEvery` is how often the scope flushes to the registry.

- **Build 2 - the service stubs.** Pass `stubsOptions` to
  `WorkflowServiceStubs.newServiceStubs(...)` (add your `setTarget` and TLS as
  usual). The `WorkflowClient` and every `WorkerFactory` built on those stubs
  inherit the scope, so this is once per process, before any Worker starts. No
  scope, no metrics: the default is a no-op scope.

- **Build 3 - the exporter.** The JDK's built-in `HttpServer` is enough to
  serve a scrape endpoint; in a Spring Boot app, Actuator does this for you.
  From here, scrape it with Prometheus, point a Datadog Agent at it, or run an
  OpenTelemetry Collector. For push instead of pull, swap in another Micrometer
  registry (Datadog, OTLP and so on).

Logging needs no setup here: the SDK logs through SLF4J, so Worker logs go
wherever your SLF4J backend (Logback, Log4j 2) sends the rest of your logs.

Metrics are not traces. For distributed tracing across Client, Workflow and
Activity, add the `io.temporal:temporal-opentracing` module: its
`OpenTracingClientInterceptor` and `OpenTracingWorkerInterceptor` create spans
and propagate the trace context through Temporal's headers (OpenTelemetry works
through its OpenTracing shim). Search attributes and the Temporal UI cover the
other question operators ask, "which Workflows are in this state right now?"

`ObservabilityMetricsTest` starts this exporter, runs a Workflow against the
test server on these stubs options, and scrapes every metric name on the SDK
metrics slides from `http://127.0.0.1:9000/metrics`.

Code: snippets/src/main/java/io/temporal/devdays/essentials/observability/SdkMetrics.java
-->

---
layout: code-walkthrough
heading: Logging from a Workflow
notes:
  - The Workflow logger comes from <code>Workflow.getLogger</code>, an SLF4J <code>Logger</code> that knows about replay
  - Log through SLF4J as usual. The SDK puts the Workflow ID, run ID, type and Task Queue in the MDC
  - Not repeated on replay. This line runs again on every replay, but logs once
---

::code::

```java {all|2|11|11,13}
public class OrderWorkflowImpl implements OrderWorkflow {
  private static final Logger log = Workflow.getLogger(OrderWorkflowImpl.class);

  private final OrderActivities activities =
      Workflow.newActivityStub(
          OrderActivities.class,
          ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

  @Override
  public String processOrder(String orderId) {
    log.info("Order received: {}", orderId);
    String receipt = activities.chargeCard(orderId);
    log.info("Order charged: {} {}", orderId, receipt);
    return receipt;
  }
}
```

<!--
Source: new for this Deck; no PPT slide.

- **Build 1 - the logger.** `Workflow.getLogger` wraps an ordinary SLF4J
  `Logger` (it is `org.slf4j.Logger` on the slide) so that it knows whether the
  Workflow is replaying. A logger from `LoggerFactory.getLogger` would work, but
  would log every line again on every replay.

- **Build 2 - context.** Use SLF4J placeholders as usual. While Workflow code
  runs, the SDK puts `WorkflowId`, `RunId`, `WorkflowType`, `TaskQueue` and
  `Namespace` in the SLF4J MDC, so a pattern with `%X{WorkflowId}` filters one
  execution's lines out of a busy Worker's logs. That needs a backend with MDC
  support, such as Logback or Log4j 2; `slf4j-simple` drops the MDC.

- **Build 3 - replay.** When a Worker replays this Workflow (after a cache
  eviction, or on a different Worker), both `log.info` lines execute again. The
  replay-aware logger drops log calls made during replay, so each line appears
  once per execution, not once per replay.
  (`WorkerFactoryOptions.setEnableLoggingInReplay(true)` turns that off, for
  debugging.) `ObservabilityLoggingTest` replays the history with
  `WorkflowReplayer` and proves the method runs again but the lines do not
  repeat.

Code: snippets/src/main/java/io/temporal/devdays/essentials/observability/OrderWorkflowImpl.java
-->

---
layout: code-walkthrough
heading: Logging from an Activity
notes:
  - An Activity logs with a plain SLF4J logger from <code>LoggerFactory</code>
  - The SDK puts the Activity type, the attempt number and the Workflow it belongs to in the MDC for each line
---

::code::

```java {all|2|6-8}
public class OrderActivitiesImpl implements OrderActivities {
  private static final Logger log = LoggerFactory.getLogger(OrderActivitiesImpl.class);

  @Override
  public String chargeCard(String orderId) {
    log.info("Charging card: {}", orderId);
    String receipt = "receipt-" + orderId;
    log.info("Card charged: {}", receipt);
    return receipt;
  }
}
```

<!--
Source: new for this Deck; no PPT slide.

- **Build 1 - the logger.** Activities are ordinary Java, and they are not
  replayed, so an ordinary SLF4J logger is the right one. No Temporal wrapper.

- **Build 2 - context.** While the Activity runs, the SDK puts `ActivityId`,
  `ActivityType`, `Attempt`, and the parent `WorkflowId`, `RunId` and
  `WorkflowType` in the MDC. Each retry is a new attempt, and logs again with
  the attempt number, which is exactly what you want when you debug a flaky
  downstream call. As on the last slide, the MDC needs a backend that supports
  it.

Code: snippets/src/main/java/io/temporal/devdays/essentials/observability/OrderActivitiesImpl.java
-->
