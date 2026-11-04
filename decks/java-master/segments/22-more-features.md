---
layout: section
---

<ZiggyHero scene="orbit">

# More Features

Beyond the fundamentals: more of what the platform does for you.

</ZiggyHero>

<!--
Ported from the Python Deck's More Features section, with every API checked
against the TypeScript SDK docs.

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
Source: docs.temporal.io/develop/typescript/workflows/schedules.

The comparison to land is cron. A crontab entry runs a command on one machine;
if that machine is down at 9:00, the run is gone and nobody is told. A
Schedule is state on the Temporal Service, and what it starts is an ordinary
Workflow Execution, so everything from the morning applies: retries, history,
visibility.
-->

---
layout: code-walkthrough
heading: Schedules in TypeScript
notes:
  - A Schedule lives on the Temporal Service under its own ID. No cron box to keep alive
  - "The action: start this Workflow, on this Task Queue, each time the Schedule fires"
  - "The spec: when. Cron expressions, intervals, or calendars"
  - "The policy: what to do if the last run is still going. <code>SKIP</code> is the default"
---

::code::

```ts {all|1-2|3-7|8|9}
const schedule = await client.schedule.create({
  scheduleId: "daily-report",
  action: {
    type: "startWorkflow",
    workflowType: reportWorkflow,
    taskQueue: "reports",
  },
  spec: { cronExpressions: ["0 9 * * *"] },
  policies: { overlap: ScheduleOverlapPolicy.SKIP },
});
```

<!--
Source: docs.temporal.io/develop/typescript/workflows/schedules. The tests
behind this Deck create this Schedule, check its next run is at 09:00 UTC, and
trigger it once to watch a Workflow run.

Each run is a normal Workflow Execution with its own history, retries and
visibility. Temporal appends the scheduled time to the Workflow ID, so runs do
not collide.

Everything after creation goes through the handle, or
`client.schedule.getHandle("daily-report")` later: `pause()`, `unpause()`,
`trigger()` to run once now, `backfill()` to run for a missed window,
`update()`, `describe()` and `delete()`. The same verbs are on
`temporal schedule` in the CLI. Creating a Schedule whose ID already exists
throws `ScheduleAlreadyRunning`; catch it rather than describing first.

`intervals: [{ every: "1 hour" }]` is the alternative to a cron string, and
`calendars` covers "every Wednesday at 20:30".

Prefer Schedules over the older `cronSchedule` option on `workflow.start`. The
docs recommend them: they can be paused, updated and backfilled.
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
Source: docs.temporal.io/develop/typescript/workers/serverless-workers and
docs.temporal.io/serverless-workers. AWS Lambda and GCP Cloud Run support are in
Public Preview; check the docs before delivering.

The case: running a Worker means choosing hosting, sizing compute, writing
scaling policies and building a deploy pipeline before the first Workflow runs
in production. A Serverless Worker hands invocation and scaling to Temporal and
the cloud provider. It fits bursty, event-driven and low-volume work best;
steady high-throughput work is usually cheaper on long-lived Workers.
-->

---
layout: code-walkthrough
heading: Serverless Workers in TypeScript
notes:
  - "<code>runWorker</code> returns a Lambda handler. Temporal invokes it when Tasks arrive and no Worker is free"
  - A Deployment Version is required. Every Serverless Worker uses Worker Versioning
  - Same registration as any Worker. A Task Queue and the Activities
  - "<code>workflowBundle</code>: Workflows bundled at build time, so a cold start skips webpack"
---

::code::

```ts {all|1|2|4,8|5-7}
export const handler = runWorker(
  { deploymentName: "orders", buildId: "build-1" },
  (config) => {
    config.workerOptions.taskQueue = "orders";
    config.workerOptions.workflowBundle = {
      codePath: require.resolve("./workflow-bundle.js"),
    };
    config.workerOptions.activities = activities;
  },
);
```

<!--
Source: docs.temporal.io/develop/typescript/workers/serverless-workers/aws-lambda.
`runWorker` is from the `@temporalio/lambda-worker` package, in Public Preview;
APIs may change before the stable release. The tests behind this Deck
type-check this against the package; it only runs inside Lambda.

How it works: a server component watches the Task Queue. When a Task arrives
and no Worker is free to take it, it invokes your Lambda. Each invocation
starts a Worker, processes Tasks, and shuts down gracefully before the Lambda
deadline. No work, no compute.

The bundle is built in a separate step with `bundleWorkflowCode` from
`@temporalio/worker`. The package sets Lambda-sized defaults (two concurrent
Activities, ten Workflow Tasks) and loads the connection from `temporal.toml`
and environment variables. Workflows default to the `PINNED` versioning
behavior. Setup the slide leaves out: an IAM role Temporal can assume to invoke
the function, and a compute provider on the Worker Deployment Version.
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
      <span>Vercel AI SDK</span>
      <span>OpenAI Agents SDK</span>
      <span>Google ADK</span>
      <span>Strands Agents</span>
      <span>Mastra</span>
    </div>
  </section>
  <section>
    <h3>Agent observability</h3>
    <div class="chips">
      <span>LangSmith</span>
      <span>Braintrust</span>
      <span>Laminar</span>
      <span>Parseable</span>
    </div>
  </section>
  <section>
    <h3>Gateways and memory</h3>
    <div class="chips">
      <span>OpenRouter</span>
      <span>xMemory</span>
    </div>
  </section>
  <section class="own">
    <h3>Not on the list?</h3>
    <p>Most of these are built on the SDK's <strong>Plugin system</strong>. You can build your own the same way.</p>
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
Source: docs.temporal.io/develop/typescript/integrations, as of 2026-11-02. The
list grows; check it before delivering and add what is new.

Why agents need this: an agent is a long-running loop of model calls and tool
calls. Models are rate-limited and flaky, tools call real systems, and a crash
halfway through a 20-step task is expensive to start over. An LLM call is I/O,
so it belongs in an Activity, which is the rule from the Design Patterns
section. The integrations do that wiring for you.

The one to show a TypeScript room is the Vercel AI SDK
(`@temporalio/vercel-ai-sdk`): register `AiSdkPlugin` on the Worker, and in the
Workflow pass `temporalProvider.languageModel("gpt-4o-mini")` as the model to
`generateText`. Two lines change, and every model call becomes an Activity.
The OpenAI Agents SDK integration (`@temporalio/openai-agents`) is in
Pre-release for TypeScript. Mastra and the observability tools are maintained
by those projects; the rest live in temporalio/ai-integrations.
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
heading: Priority and Fairness in TypeScript
notes:
  - "<code>priorityKey</code>: 1 to 5, lower dispatches first, default 3. Payments cut ahead of batch work"
  - "<code>fairnessKey</code>: one virtual queue per tenant, dispatched in proportion to <code>fairnessWeight</code>. A big tenant can't starve the rest"
---

::code::

```ts {all|5|6-7}
export async function orderWorkflow(order: Order): Promise<string> {
  const { chargePayment } = proxyActivities<typeof activities>({
    startToCloseTimeout: "10 seconds",
    priority: {
      priorityKey: 1,
      fairnessKey: order.tenantId,
      fairnessWeight: 2,
    },
  });
  return await chargePayment(order);
}
```

<!--
Source: docs.temporal.io/develop/task-queue-priority-fairness. The tests behind
this Deck run this Workflow and find all three values on the
ActivityTaskScheduled Event.

- **Priority** is strict. All priority-1 Tasks dispatch before the first
  priority-2 Task.
- **Fairness** works within a priority level. Each key is a virtual queue, and
  keys take turns in proportion to their weights: weights of 5, 3 and 2 give
  roughly 50%, 30% and 20% of dispatches while all three have a backlog.

`proxyActivities` is called inside the Workflow here because the fairness key
comes from the input. Set `priority` the same way on `client.workflow.start`
or `startChild`. Activities and Child Workflows inherit their Workflow's
Priority unless they set their own.

Neither is a rate limit or an isolation boundary. For hard isolation, use a
separate Task Queue with its own Workers.
-->
