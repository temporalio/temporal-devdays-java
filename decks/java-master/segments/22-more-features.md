---
layout: section
---

<ZiggyHero scene="orbit">

# More Features

Beyond the fundamentals: more of what the platform does for you.

</ZiggyHero>

<!--
Every API in this section is checked against the Java SDK 1.40.0 sources and
the Java docs.

A tour, not a lab: features people ask about once the fundamentals land. Each
gets a "what it is, why it matters" slide, then its code where code helps.
Nothing after this section depends on it. The divider names no features, so
adding one to the section means adding its slides, not editing this one.
-->

---
layout: default
---

# Schedules

<WhatWhy>
  <template #what>

A Schedule starts a Workflow on a timetable: a cron expression, a fixed
interval, or a calendar. It lives on the Temporal Service, not on a machine.

  </template>
  <template #why>
    <li><strong>Nothing to babysit.</strong> No cron host whose outage silently skips a run</li>
    <li><strong>Every run is a Workflow Execution</strong>, with retries, Event History and a place in the UI</li>
    <li><strong>Operable.</strong> Pause, trigger now, backfill a missed window, or update it, from code, the CLI or the UI</li>
    <li><strong>An overlap policy</strong> decides what happens when the last run is still going</li>
  </template>
</WhatWhy>

<!--
Source: docs.temporal.io/develop/java/workflows/schedules.

The comparison to land is cron. A crontab entry runs a command on one machine;
if that machine is down at 9:00, the run is gone and nobody is told. A
Schedule is state on the Temporal Service, and what it starts is an ordinary
Workflow Execution, so everything from the morning applies: retries, history,
visibility.
-->

---
layout: code-walkthrough
heading: Schedules in Java
notes:
  - "A Schedule lives on the Temporal Service under its own ID. <code>createSchedule</code>, and no cron box to keep alive"
  - "The action: start this Workflow, on this Task Queue, each time the Schedule fires"
  - "The spec: when. <code>setCronExpressions</code>, intervals, or calendars"
  - "The policy: what to do if the last run is still going. <code>SKIP</code> is the default"
---

::code::

```java {all|1-3|5-13|14|15-16}
ScheduleHandle schedule =
    scheduleClient.createSchedule(
        "daily-report",
        Schedule.newBuilder()
            .setAction(
                ScheduleActionStartWorkflow.newBuilder()
                    .setWorkflowType(ReportWorkflow.class)
                    .setOptions(
                        WorkflowOptions.newBuilder()
                            .setWorkflowId("daily-report")
                            .setTaskQueue("reports")
                            .build())
                    .build())
            .setSpec(ScheduleSpec.newBuilder().setCronExpressions(List.of("0 9 * * *")).build())
            .setPolicy(
                SchedulePolicy.newBuilder().setOverlap(SCHEDULE_OVERLAP_POLICY_SKIP).build())
            .build(),
        ScheduleOptions.newBuilder().build());
```

<!--
Source: docs.temporal.io/develop/java/workflows/schedules.
Code: snippets/src/main/java/io/temporal/devdays/essentials/features/DailyReportSchedule.java.
The tests behind this Deck create this Schedule on a dev server, check its next
run is at 09:00 UTC, and trigger it once to watch a Workflow run.

The client is `ScheduleClient.newInstance(service)`, built from the same
`WorkflowServiceStubs` as your `WorkflowClient`. `SCHEDULE_OVERLAP_POLICY_SKIP`
is statically imported from `io.temporal.api.enums.v1.ScheduleOverlapPolicy`.

Each run is a normal Workflow Execution with its own history, retries and
visibility. The Workflow ID is required, and Temporal appends the scheduled
time to it, so runs do not collide.

Everything after creation goes through the handle, or
`scheduleClient.getHandle("daily-report")` later: `pause()`, `unpause()`,
`trigger()` to run once now, `backfill(...)` to run for a missed window,
`update(...)`, `describe()` and `delete()`. The same verbs are on
`temporal schedule` in the CLI. Creating a Schedule whose ID already exists
throws `ScheduleAlreadyRunningException`; catch it rather than describing
first.

`setIntervals(List.of(new ScheduleIntervalSpec(Duration.ofHours(1))))` is the
alternative to a cron string, and `setCalendars(...)` covers "every Wednesday
at 20:30".

Prefer Schedules over the older `setCronSchedule` option on `WorkflowOptions`.
The docs recommend them: they can be paused, updated and backfilled.
-->

---
layout: default
---

# Serverless Workers

<WhatWhy>
  <template #what>

Workers that run on serverless compute (AWS Lambda, GCP Cloud Run). Temporal
starts them when Tasks arrive and lets them stop when the work drains.
_Public Preview._

  </template>
  <template #why>
    <li><strong>No Worker fleet</strong> to provision, scale, patch or keep running idle</li>
    <li><strong>Scales with demand</strong>, down to zero, so you pay for compute only while work is in flight</li>
    <li><strong>Same SDK, same code.</strong> Workflows and Activities register exactly as they do today</li>
    <li><strong>Spillover capacity.</strong> Share a Task Queue with long-lived Workers and absorb bursts</li>
  </template>
</WhatWhy>

<!--
Source: docs.temporal.io/develop/java/workers/serverless-workers and
docs.temporal.io/serverless-workers. AWS Lambda and GCP Cloud Run support are in
Public Preview; check the docs before delivering. The Java modules are
`io.temporal:temporal-aws-lambda` and `io.temporal:temporal-gcp-cloud-run`.

The case: running a Worker means choosing hosting, sizing compute, writing
scaling policies and building a deploy pipeline before the first Workflow runs
in production. A Serverless Worker hands invocation and scaling to Temporal and
the cloud provider. It fits bursty, event-driven and low-volume work best;
steady high-throughput work is usually cheaper on long-lived Workers.
-->

---
layout: code-walkthrough
heading: Serverless Workers in Java
notes:
  - "<code>LambdaWorker.define</code> builds a Lambda handler once, at cold start. Temporal invokes it when Tasks arrive and no Worker is free"
  - "A <code>WorkerDeploymentVersion</code> is required. Every Serverless Worker uses Worker Versioning"
  - Same registration as any Worker. A Task Queue, the Workflows and the Activities
  - "<code>handleRequest</code>: each invocation starts a Worker, polls, and shuts down before the Lambda deadline"
---

::code::

```java {all|2-3|4|6-8|12-13}
public class OrdersHandler implements RequestHandler<Object, Void> {
  private static final RequestHandler<Object, Void> WORKER =
      LambdaWorker.define(
          new WorkerDeploymentVersion("orders", "build-1"),
          builder -> {
            builder.setTaskQueue("orders");
            builder.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
            builder.registerActivitiesImplementations(new OrderActivitiesImpl());
          });

  @Override
  public Void handleRequest(Object input, Context context) {
    return WORKER.handleRequest(input, context);
  }
}
```

<!--
Source: docs.temporal.io/develop/java/workers/serverless-workers/aws-lambda.
Code: snippets/src/main/java/io/temporal/devdays/essentials/features/OrdersHandler.java.
`LambdaWorker` is from the `io.temporal:temporal-aws-lambda` module, in Public
Preview; APIs may change before the stable release. The tests behind this Deck
compile this against the 1.40.0 module; it only runs inside Lambda. The full
project is samples-java's `lambda-worker`.

How it works: a server component watches the Task Queue. When a Task arrives
and no Worker is free to take it, it invokes your Lambda. Each invocation
starts a Worker, processes Tasks, and shuts down gracefully before the Lambda
deadline. No work, no compute. The handler lives in a static field so it is
built once per cold start and reused across invocations.

The module sets Lambda-sized defaults (two concurrent Activities, ten
Workflow Tasks, a Workflow cache of 30) and loads the connection from
`temporal.toml` and `TEMPORAL_*` environment variables. Workflows default to
the `PINNED` versioning behavior. Setup the slide leaves out: an IAM role
Temporal can assume to invoke the function, and a compute provider on the
Worker Deployment Version. Java Workers want more memory than the docs'
minimum: start the function at 1024 MB.
-->

---
layout: default
---

# AI integrations

<p class="ai-lede">
Durable Execution for agents: the agent loop runs as a Workflow, and every
model and tool call runs as an Activity. Agents survive crashes, retry
rate-limited models, and never pay twice for a call that already finished.
</p>

<div class="ai-groups">
  <section>
    <h3>Agent frameworks</h3>
    <div class="chips">
      <span>Spring AI</span>
      <span>Google ADK</span>
    </div>
  </section>
  <section>
    <h3>Agent observability</h3>
    <div class="chips">
      <span>Parseable</span>
    </div>
  </section>
  <section>
    <h3>Application frameworks</h3>
    <div class="chips">
      <span>Spring Boot</span>
    </div>
  </section>
  <section class="own">
    <h3>Not on the list?</h3>
    <p>These are built on the SDK's <strong>Plugin system</strong>. You can build your own the same way.</p>
  </section>
</div>

<style>
.ai-lede {
  max-width: 46rem;
  font-size: 1rem !important;
  line-height: 1.5;
  color: var(--temporal-text-strong);
}
.ai-groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 1rem;
}
.ai-groups section {
  border-radius: 0.7rem;
  padding: 0.8rem 1rem;
  background: var(--temporal-bg-elev);
  border: 1px solid var(--temporal-border-hi);
}
.ai-groups h3 {
  margin: 0 0 0.55rem !important;
  font-size: 0.66rem !important;
  font-weight: 500;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--temporal-green);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.chips span {
  font-size: 0.78rem;
  padding: 0.2rem 0.65rem;
  border-radius: 999px;
  border: 1px solid var(--temporal-lavender);
  background: rgb(154 128 255 / 14%);
  color: #fff;
}
.own p {
  margin: 0 !important;
  font-size: 0.85rem !important;
  color: var(--temporal-text-strong);
}
</style>

<!--
Source: docs.temporal.io/develop/java/integrations and
docs.temporal.io/develop/java/integrations/spring-ai, as of 2026-11-04, plus
the io.temporal:google-adk module on Maven Central. The list grows; check it
before delivering and add what is new.

Why agents need this: an agent is a long-running loop of model calls and tool
calls. Models are rate-limited and flaky, tools call real systems, and a crash
halfway through a 20-step task is expensive to start over. An LLM call is I/O,
so it belongs in an Activity, which is the rule from the Design Patterns
section. The integrations do that wiring for you.

The one to show a Java room is Spring AI (`io.temporal:spring-ai`, Public
Preview): with the Spring Boot starter, `SpringAiPlugin` registers a
`ChatModelActivity` on every Worker. In the Workflow, build the chat client
from `ActivityChatModel.forDefault()` with
`TemporalChatClient.builder(chatModel)`, and every model call becomes an
Activity. Tools are dispatched by type: an `@ActivityInterface` stub with
`@Tool` methods runs as Activities, `@SideEffectTool` classes run in
`Workflow.sideEffect`. It needs Spring AI 2, Spring Boot 4 and SDK 1.40.0;
Spring AI 1 applications stay on `io.temporal:temporal-spring-ai:1.40.x`.
samples-java has `springai/basic`, `mcp`, `multimodel` and `rag`.

Google ADK for Java (`io.temporal:google-adk`) is in Public Preview in
temporalio/ai-integrations and not yet on the docs' integrations page.
Parseable is maintained by Parseable. The Plugin system is `SimplePlugin` in
`io.temporal.common`.
-->

---
layout: default
---

# Priority and Fairness

<WhatWhy>
  <template #what>

Two controls over the order Tasks leave a shared Task Queue. **Priority**:
levels 1 to 5, and higher-priority Tasks always go first. **Fairness**:
within a level, tenants take turns in proportion to their weight.

  </template>
  <template #why>
    <li><strong>Urgent work cuts the line.</strong> A payment no longer waits behind a bulk import</li>
    <li><strong>No noisy neighbours.</strong> One big tenant's backlog can't starve the small ones</li>
    <li><strong>One Worker pool</strong> serves every tier, with no Task Queue per tenant to configure and route</li>
    <li><strong>New tenants are just new keys.</strong> No new queues or Workers to deploy</li>
  </template>
</WhatWhy>

<!--
Source: docs.temporal.io/develop/task-queue-priority-fairness and
docs.temporal.io/design-patterns/fairness.

The classic workaround for both problems is a Task Queue per tier or per
tenant. That means a Worker pool, routing logic and idle capacity for every
one of them. Priority and Fairness do the same job inside one queue.

Priority is on by default, on Temporal Cloud and self-hosted. Fairness is
enabled per Namespace, and on Temporal Cloud it is a paid feature.
-->

---
layout: code-walkthrough
heading: Priority and Fairness in Java
notes:
  - "<code>setPriorityKey</code>: 1 to 5, lower dispatches first, default 3. Payments cut ahead of batch work"
  - "<code>setFairnessKey</code>: one virtual queue per tenant, dispatched in proportion to <code>setFairnessWeight</code>. A big tenant can't starve the rest"
---

::code::

```java {all|11|12-13}
public class OrderWorkflowImpl implements OrderWorkflow {
  @Override
  public String processOrder(Order order) {
    OrderActivities activities =
        Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setPriority(
                    Priority.newBuilder()
                        .setPriorityKey(1)
                        .setFairnessKey(order.tenantId())
                        .setFairnessWeight(2)
                        .build())
                .build());
    return activities.chargePayment(order);
  }
}
```

<!--
Source: docs.temporal.io/develop/task-queue-priority-fairness, and the javadoc
of `io.temporal.common.Priority` in SDK 1.40.0.
Code: snippets/src/main/java/io/temporal/devdays/essentials/features/OrderWorkflowImpl.java.
The tests behind this Deck run this Workflow and find all three values on the
ActivityTaskScheduled Event.

- **Priority** is strict. All priority-1 Tasks dispatch before the first
  priority-2 Task. Unset (or 0) means 3, the midpoint of the default 1 to 5.
- **Fairness** works within a priority level. Each key is a virtual queue, and
  keys take turns in proportion to their weights: weights of 5, 3 and 2 give
  roughly 50%, 30% and 20% of dispatches while all three have a backlog. The
  default weight is 1.0, clamped to 0.001 to 1000; keys are at most 64 bytes.

The stub is built inside the Workflow method here because the fairness key
comes from the input. Set `setPriority` the same way on `WorkflowOptions` or
`ChildWorkflowOptions`. Activities and Child Workflows inherit their Workflow's
Priority unless they set their own.

Neither is a rate limit or an isolation boundary. For hard isolation, use a
separate Task Queue with its own Workers.
-->
