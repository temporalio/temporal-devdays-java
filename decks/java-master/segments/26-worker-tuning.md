---
layout: section
---

<ZiggyHero scene="tune">

# Worker tuning

</ZiggyHero>

<!--
Source: PPT slides 187-206.

The defaults on these slides are the Java SDK's, read from the
`temporal-sdk` 1.40.0 source (`WorkerOptions`, `WorkerFactoryOptions`). If you
deliver this against a newer SDK, re-check them: `TuningTest` asserts every
default in the tables, and `TuningOptions.java` compiles every option name the
slides use.
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

In a Java Worker, everything in the diagram is threads. Each poller is a
thread holding a long-poll open. Workflow Tasks run on the Workflow Task
executor, and each Workflow's own code runs on Workflow threads from a pool
shared by every Worker in the `WorkerFactory` (and kept while the Workflow is
cached). Activities run on the Activity executor, one thread per running
Activity: platform threads by default, or virtual threads with
`WorkerOptions.setUsingVirtualThreadsOnActivityWorker(true)` on Java 21.

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

What one Workflow Task does inside the Worker. This is Temporal's loop, not a
language runtime's: the Java SDK runs it in its deterministic runner. Signals,
Updates and Queries arrive from outside and are handled at defined points.
Handlers run first, then the Workflow's threads run one at a time until every
one is blocked on something the SDK controls (`Workflow.sleep`, an Activity
stub call, `Workflow.await`, `Promise.get()`), then Queries are answered.
Repeat until the Workflow completes or continues as new.

"Until blocked" is why CPU-heavy or blocking code in a Workflow is so harmful:
the SDK only gets control back when the code calls into it. A busy loop,
`Thread.sleep`, or real IO holds the Task. After 1 second without yielding
(`WorkerOptions.setDefaultDeadlockDetectionTimeout`, default 1000 ms) the
Worker fails the Workflow Task with `[TMPRL1101] Potential deadlock detected`;
the Task is retried and fails the same way until the code changes.
`TuningTest` proves it, and that the Workflow stays open meanwhile.
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

| Setting                                    | Java default |
| ------------------------------------------ | -----------: |
| `setMaxConcurrentWorkflowTaskExecutionSize` |          200 |
| `setMaxConcurrentActivityExecutionSize`    |          200 |
| `setMaxConcurrentLocalActivityExecutionSize` |        200 |
| `spec.containers[].resources.limits.cpu`   | yours to set |

<!--
Source: PPT slides 196-197.

These are execution slots: how many Tasks of each kind this Worker will run at
once. They are `WorkerOptions.newBuilder()` setters, passed to
`factory.newWorker(taskQueue, options)`.

Two Java-specific notes:

- Each running Activity holds a thread. For Activities that mostly wait on IO,
  200 concurrent is cheap, and on Java 21
  `setUsingVirtualThreadsOnActivityWorker(true)` makes it cheaper still. But
  CPU-heavy Activities (big JSON parsing, hashing, image processing) compete
  for the same cores, and 200 of them at once just thrash. Give them their own
  Worker on their own Task Queue with a low
  `setMaxConcurrentActivityExecutionSize`, close to the core count.
- Workflow Tasks do run in parallel, up to the slot count, but CPU-heavy
  Workflow code is a bug in its own right: it trips the deadlock detector after
  1 second (previous section). More Worker Processes, not more slots, is the
  answer when Workflow Tasks are CPU-bound.
-->

---
layout: code-stack
heading: Execution slot auto-tuning
proseMax: 36
---

::code::

```java
Worker worker =
    factory.newWorker(
        "order-task-queue",
        WorkerOptions.newBuilder()
            .setWorkerTuner(
                ResourceBasedTuner.newBuilder()
                    .setControllerOptions(
                        ResourceBasedControllerOptions.newBuilder(0.8, 0.8).build())
                    .build())
            .build());
```

::default::

Part of Worker autotuning, which aims to get the most out of each Worker's
resources. Slots are handed out by how close the Worker is to a resource
limit: the percentage of CPU and memory in use.

<!--
Source: PPT slide 198.

`ResourceBasedControllerOptions.newBuilder(0.8, 0.8)` is target memory use,
then target CPU use, as fractions of the machine. Out of the box the tuner
allows between 5 and 500 Workflow Task slots, and between 1 and 1,000 Activity
and Local Activity slots (`ResourceBasedTuner.DEFAULT_*_SLOT_OPTIONS`);
`setWorkflowSlotOptions` and friends change those bounds.

`setWorkerTuner` is mutually exclusive with the
`setMaxConcurrent*ExecutionSize` setters on the previous slide. Pick one
approach: `newWorker` throws `IllegalStateException` if you set both
(`TuningTest` proves it).

For most workloads Temporal's docs recommend fixed-size slots, tuned to your
hardware: they perform better and behave more predictably. The resource-based
tuner suits fluctuating workloads where each Task uses little CPU or memory but
may wait a long time (HTTP calls, other IO), and works as protection against
out-of-memory crashes when per-Task use is unpredictable. Work with very high
or highly variable per-Task cost belongs on fixed slots, tuned by hand.

One catch: `temporal_worker_task_slots_available` only reports for fixed-size
slots. With a resource-based tuner, watch `temporal_worker_task_slots_used`
and the `temporal_resource_slots_*` gauges instead.

To mix them, build a `CompositeTuner` from one slot supplier per Task type, in
the order Workflow, Activity, Local Activity, Nexus: for example
`new FixedSizeSlotSupplier<>(200)` for Workflows and
`ResourceBasedSlotSupplier.createForActivity(controller, options)` for
Activities. Every resource-based supplier must share one
`ResourceBasedController`. `TuningOptions.composite()` compiles that example.

Code: snippets/src/main/java/io/temporal/devdays/essentials/tuning/TunedWorker.java
-->

---
layout: default
---

# Memory settings

| Setting                                      |      Java default |
| -------------------------------------------- | ----------------: |
| `setWorkflowCacheSize` (factory)             |               600 |
| `setMaxWorkflowThreadCount` (factory)        |               600 |
| `-Xmx` / `-XX:MaxRAMPercentage`              | JVM: 25% of RAM   |
| `spec.containers[].resources.limits.memory`  |      yours to set |

<!--
Source: PPT slides 199-200.

`setWorkflowCacheSize` is the sticky cache from the observability segment.
Both it and `setMaxWorkflowThreadCount` are `WorkerFactoryOptions`, shared by
every Worker the factory creates. Each cached Workflow holds its state, and its
parked Workflow threads, in memory, so the right number depends on how big
your Workflows are. Usually the thread count caps the cache first. With
`setUsingVirtualWorkflowThreads(true)` (Java 21) the thread cap is ignored and
the cache size is the limit.

The heap is the JVM's, not Temporal's: by default a container-aware JVM takes
25% of the container's memory for heap (`MaxRAMPercentage`). Set `-Xmx` or
`-XX:MaxRAMPercentage` deliberately, leaving room under the container limit
for thread stacks, metaspace and direct buffers. Watch
`temporal_sticky_cache_size` against heap use and adjust.
-->

---
layout: default
---

# IO settings

| Setting                                | Java default |
| -------------------------------------- | -----------: |
| `setMaxConcurrentWorkflowTaskPollers`  |            5 |
| `setMaxConcurrentActivityTaskPollers`  |            5 |
| `setMaxTaskQueueActivitiesPerSecond`   |    unlimited |
| Namespace APS                          | set per Namespace |

<!--
Source: PPT slides 201-202.

Pollers are the IO side: how many long-polls the Worker keeps open to the
server. Workflow Task pollers alternate between the normal and the sticky
queue, so that number is never below 2 (a 1 is raised to 2).

The newer way is a poller behaviour:
`setWorkflowTaskPollersBehavior(new PollerBehaviorAutoscaling())` (and the
Activity equivalent) lets the server tell the Worker how many pollers it needs,
between 1 and 100 by default, starting at 5. Worth trying before hand-tuning
the maximum. It is mutually exclusive with the fixed poller count.
`new PollerBehaviorSimpleMaximum(n)` is the fixed count expressed as a
behaviour. If you set neither and your Namespace auto-enrolls Workers into
poller autoscaling, the Worker uses autoscaling.

`setMaxTaskQueueActivitiesPerSecond` is a server-side limit across every Worker
on the Task Queue, which is how you protect a fragile downstream API.
`setMaxWorkerActivitiesPerSecond` is the per-Worker equivalent. Namespace APS
(Actions Per Second) is a Temporal Cloud limit on your Namespace, set by your
account.
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
being OOM-killed and restarted.

In Java, "out of memory" means one of two things, and they need different
fixes. `java.lang.OutOfMemoryError: Java heap space` is the JVM hitting its own
`-Xmx`: raise the heap or shrink the cache. A container OOM-kill (exit code
137, no Java stack trace) is the whole process, heap plus thread stacks plus
metaspace, going over the container limit: the heap is set too close to the
limit. Add `-XX:+ExitOnOutOfMemoryError` so a Worker that runs out of heap
restarts cleanly instead of limping on.
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

- `setMaxConcurrentWorkflowTaskExecutionSize` → `temporal_worker_task_slots_available`
  with `worker_type="WorkflowWorker"`
- `setMaxConcurrentActivityExecutionSize` → `temporal_worker_task_slots_available`
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
constraints slide). `ObservabilityMetricsTest` checks both `worker_type` tags.

On a JVM, also watch garbage collection: bind Micrometer's `JvmGcMetrics` and
`JvmMemoryMetrics` to the same registry and graph `jvm_gc_pause_seconds`. Long
GC pauses stall every thread in the Worker, pollers included, and show up there
before they show up in Temporal's metrics.
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
