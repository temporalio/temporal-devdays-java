---
layout: section
---

<ZiggyHero scene="tune">

# Worker tuning

</ZiggyHero>

<!--
Source: PPT slides 187-206. Ported from the Python Deck.

The defaults on these slides are the TypeScript SDK's, checked against
`@temporalio/worker` 1.24. If you deliver this against a newer SDK, re-check
them; the snippets package type-checks every option name the slides use.
-->

---
layout: default
---

# How a Worker works

Workers poll a Task Queue in Temporal, execute Tasks, and respond with the result.

<img src="/images/worker-internals.png" alt="Inside a Temporal Worker: Workflow and Activity Task pollers poll their Task Queues in Temporal Cloud, hand Tasks to handlers with execution slots, cache Workflows, and respond with the results" class="h-[340px] mx-auto rounded" />

<!--
Source: PPT slides 188-191.

Walk the numbers on the diagram:

1. Workflow Task pollers long-poll the Workflow Task Queue.
2. A Task arrives and is handed to the Workflow Task handler, which needs a free
   execution slot.
3. If this Worker has the Workflow cached, it loads the state from the cache
   rather than replaying the whole history.
4. After the Task, the Workflow state goes back in the cache.
5. The Worker responds with the commands the Workflow produced.

Activity Tasks work the same way, minus the cache.

In a TypeScript Worker, the polling, slots and cache bookkeeping live in the
SDK's Rust core; your Workflow code runs in V8 isolates on a worker thread, and
your Activities run on the Node.js main thread.

The original slides 189-190 asked "what happens if a Worker goes down while
executing a Workflow?" Answer: another Worker picks up the next Workflow Task,
replays the Event History to rebuild state, and carries on. Nothing is lost,
because nothing lived only in that Worker.
-->

---
layout: default
---

# The Workflow event loop

<img src="/images/workflow-event-loop.png" alt="The Workflow event loop: initialize, then repeatedly progress Signals and Updates until blocked, progress the Workflow until blocked, execute Queries, and finally complete or continue as new" class="h-[400px] mx-auto rounded" />

<!--
Source: PPT slide 192.

What one Workflow Task does inside the Worker. Signals, Updates and Queries
arrive from outside and are handled at defined points. Handlers run first, then
the Workflow body runs until every promise is blocked on something (a Timer,
an Activity, a `condition`), then Queries are answered. Repeat until the
Workflow completes or continues as new.

"Until blocked" is why a synchronous, CPU-heavy loop in Workflow code is so
harmful: nothing else in that Task can run until it awaits. With the default
`reuseV8Context`, every Workflow on the Worker shares one V8 context on one
thread (`workflowThreadPoolSize` defaults to 1), so a busy loop holds up the
other Workflows too. After 5 seconds without yielding, the Worker gives up and
fails the Workflow Task ("Script execution timed out after 5000ms"); the Task
is retried and fails the same way until the code changes.
-->

---
layout: section
---

<ZiggyHero scene="lift">

# Worker resources

</ZiggyHero>

<!--
Source: PPT slide 193.
-->

---
layout: default
---

# Theory of constraints

<img src="/images/theory-of-constraints.png" alt="Adding capacity upstream of a bottleneck sends more work to it, so performance at the bottleneck gets worse and system throughput goes down" class="h-[380px] mx-auto rounded" />

<!--
Source: PPT slide 194.

The warning before the tuning knobs: adding capacity anywhere but the
bottleneck makes things worse. More Workflow slots in front of a saturated
database means more Activities queueing on that database. Find the constraint
first. The three places it usually lives are the next slide.
-->

---
layout: section
---

<ZiggyHero scene="trio">

# Memory · IO · Compute

</ZiggyHero>

<!--
Source: PPT slide 195.

Every Worker is bounded by one of these three. The next four slides take them in
turn.
-->

---
layout: default
---

# Compute settings

| Setting                                  | TypeScript default |
| ---------------------------------------- | -----------------: |
| `maxConcurrentWorkflowTaskExecutions`    |                 40 |
| `maxConcurrentActivityTaskExecutions`    |                100 |
| `maxConcurrentLocalActivityExecutions`   |                100 |
| `spec.containers[].resources.limits.cpu` |       yours to set |

<!--
Source: PPT slides 196-197.

These are execution slots: how many Tasks of each kind this Worker will run at
once. They are `Worker.create({...})` options.

Two Node.js-specific notes:

- Activities run on the Worker's Node.js event loop, which is single-threaded.
  For Activities that mostly `await` IO, 100 concurrent is cheap: they
  interleave on that one loop. But synchronous CPU work in an Activity (a big
  `JSON.parse`, hashing, image processing) blocks every other Activity on that
  Worker until it finishes, and they cannot heartbeat meanwhile. Move CPU-heavy
  work to `worker_threads` (a pool such as Piscina) or a child process, or give it its own Worker on its
  own Task Queue with a low `maxConcurrentActivityTaskExecutions`.
- Workflow code runs in V8 isolates on Worker threads. With the default
  `reuseV8Context: true`, one thread runs every Workflow (`workflowThreadPoolSize`
  defaults to 1), so CPU-heavy Workflow Tasks do not run in parallel within a
  process. More Worker Processes, not more slots, is usually the answer.
-->

---
layout: code-stack
heading: Execution slot auto-tuning
proseMax: 36
---

::code::

```ts
const worker = await Worker.create({
  connection,
  taskQueue: "order-task-queue",
  workflowsPath,
  activities,
  tuner: {
    tunerOptions: {
      targetMemoryUsage: 0.8,
      targetCpuUsage: 0.8,
    },
  },
});
```

::default::

Part of Worker autotuning, which aims to get the most out of each Worker's
resources. Slots are handed out by how close the Worker is to a resource
limit: the percentage of CPU and memory in use.

<!--
Source: PPT slide 198.

`tuner` is mutually exclusive with the `maxConcurrent*Executions` options on
the previous slide. Pick one approach: `Worker.create` throws if you set both.

For most workloads Temporal's docs recommend fixed-size slots, tuned to your
hardware: they perform better and behave more predictably. The resource-based
tuner suits fluctuating workloads where each Task uses little CPU or memory but
may wait a long time (HTTP calls, other IO), and works as protection against
out-of-memory crashes when per-Task use is unpredictable. Work with very high
or highly variable per-Task cost belongs on fixed slots, tuned by hand.

One catch: `temporal_worker_task_slots_available` only reports for fixed-size
slots. With a resource-based tuner, watch `temporal_worker_task_slots_used`
and the `temporal_resource_slots_*` gauges instead.

To mix them, pass a slot supplier per Task type instead of `tunerOptions`:
`workflowTaskSlotSupplier: { type: "fixed-size", numSlots: 40 }` and
`activityTaskSlotSupplier: { type: "resource-based", tunerOptions: {...} }`,
plus `localActivityTaskSlotSupplier` and `nexusTaskSlotSupplier`.
-->

---
layout: default
---

# Memory settings

| Setting                                     | TypeScript default |
| ------------------------------------------- | -----------------: |
| `maxCachedWorkflows`                        | from the Node heap |
| `spec.containers[].resources.limits.memory` |       yours to set |

<!--
Source: PPT slides 199-200.

`maxCachedWorkflows` is the sticky cache from the observability segment. Each
cached Workflow holds its state in memory, so the right number depends on how
big your Workflows are. The default is computed from the Node.js heap limit:
about 600 Workflows per GB of heap above the first 200 MB, and at least 10.
That means the heap limit you give Node (`--max-old-space-size`) changes the
cache size. Watch `temporal_sticky_cache_size` against container memory and
adjust.
-->

---
layout: default
---

# IO settings

| Setting                                | TypeScript default |
| -------------------------------------- | -----------------: |
| `workflowTaskPollerBehavior` (maximum) |                 10 |
| `activityTaskPollerBehavior` (maximum) |                 10 |
| `maxTaskQueueActivitiesPerSecond`      |          unlimited |
| Namespace APS                          |  set per Namespace |

<!--
Source: PPT slides 201-202.

Pollers are the IO side: how many long-polls the Worker keeps open to the
server. The default maximum is 10, or the slot count if that is lower. The
older `maxConcurrentWorkflowTaskPolls` and `maxConcurrentActivityTaskPolls`
options set the same simple maximum; the poller behaviours are the newer way.

`{ type: "autoscaling" }` lets the server tell the Worker how many pollers it
needs (between 1 and 100 by default, starting at 5). Worth trying before
hand-tuning the maximum.

`maxTaskQueueActivitiesPerSecond` is a server-side limit across every Worker on
the Task Queue, which is how you protect a fragile downstream API.
`maxActivitiesPerSecond` is the per-Worker equivalent. Namespace APS (Actions
Per Second) is a Temporal Cloud limit on your Namespace, set by your account.
-->

---
layout: default
---

# Failures

- **Network failures**
- **Worker crash** (for example, out of memory)

<!--
Source: PPT slide 203.

Both are what Temporal is designed to survive, and neither loses a Workflow. But
both show up in the metrics first: poll failures and request latency for the
network, and a sawtooth in `temporal_sticky_cache_size` for a Worker that keeps
being OOM-killed and restarted. In Node, an out-of-memory crash is often the
V8 heap limit, not the container limit: "JavaScript heap out of memory".
-->

---
layout: default
---

# What is sync match?

**`poll_success_sync_count`**

A sync match is when a Task is assigned to a Worker **immediately**. If no Worker
is available, Temporal Cloud persists the Task in its database first (an "async
match").

- **Sync match rate** is the proportion of Tasks assigned immediately
- 100% means lower latency and a more responsive system
- A low rate often means there aren't enough Workers for the incoming load
- Also consider scaling on **schedule-to-start latency**, a related metric

<!--
Source: PPT slide 204. From the reference guide on monitoring, alerting and
health.
-->

---
layout: default
---

# Compute settings to metrics

- `maxConcurrentWorkflowTaskExecutions` → `temporal_worker_task_slots_available`
  with `worker_type="WorkflowWorker"`
- `maxConcurrentActivityTaskExecutions` → `temporal_worker_task_slots_available`
  with `worker_type="ActivityWorker"`
- CPU-intensive logic → `temporal_workflow_task_execution_latency`

Don't forget to measure your machine's own CPU use, for example
`container_cpu_usage_seconds_total`.

<!--
Source: PPT slide 205.

How to read this: each setting on the left has a metric on the right that tells
you whether it is the constraint. Slots available near zero means raise the
slot count, or add Workers. High execution latency with slots to spare means
the CPU is the constraint, and more slots will only make it worse (the Theory of
constraints slide).

For CPU-heavy Activities in Node, also watch event-loop lag (for example
`monitorEventLoopDelay` from `node:perf_hooks`): a blocked loop shows up there
before it shows up in Temporal's metrics.
-->

---
layout: default
---

# Key takeaways

Scaling Workers is case by case, but some starting rules of thumb:

<div class="text-[0.85rem] leading-snug">

- **Schedule-to-start latency** rising, Workers at capacity: scale up
- **Task slots available** at zero for a while: scale up
- **Task Queue backlog**: watch it through the control-plane APIs
- **CPU and memory**: the signal for CPU-heavy Activity Workers
- **High volume**: book a tuning session with your Temporal solutions engineer

</div>

<!--
Source: PPT slide 206.

The bullets are the short form; say the rest:

- Schedule-to-start latency (or sync match rate) is where most resource
  contention shows up first. Rising latency with Workers at capacity means
  scale up the pool.
- Task slots available dropping to zero for a while means the Workers are fully
  used.
- Task Queue backlog gives more direct control. There is a community-built,
  KEDA-based autoscaler that reads it.
- CPU and memory are a good scaling metric for Activity Workers doing CPU-heavy
  work, though less directly tied to Temporal's own load.
-->
