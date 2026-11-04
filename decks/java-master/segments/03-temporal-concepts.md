---
layout: section
---

<ZiggyHero scene="replay">

# Temporal concepts

The four pieces, and why replay works.

</ZiggyHero>

<!--
Source: PPT slides 22-28.

Seven short slides. This is the vocabulary everything after it assumes, so
check the room is with you before the code starts.
-->

---
layout: default
---

# The big picture

<TemporalArchitecture />

<!--
Source: PPT slide 23.

The original had three bullets: the Temporal Server tracks state, schedules work
and records history; the Worker is the process you deploy that executes code;
your business logic is orchestration plus action.

- **Temporal Client** - a library in your application that sends control
  requests (start, Signal, cancel, Query) to the Server over gRPC. It runs no
  Workflow or Activity code.
- **Temporal Server** - records Event History, schedules Tasks on Task Queues,
  and persists state to a database. Frontend is the API, History owns Workflow
  state, Matching owns the Task Queues.
- **Workers** - your processes. They long-poll the Server for Tasks and run your
  Workflow and Activity code.

The three points worth making, in the order people ask:

1. **Nothing of yours runs on the Server.** It schedules and remembers. It never
   executes your code or sees your dependencies. That is the answer to the
   security review.
2. **The arrows only point outward.** Workers poll the Server. Temporal never
   opens a connection into your network, so Workers can sit in a private subnet
   behind NAT with no inbound rule, no ingress and no VPN.
3. **The database is the product.** Event History is the thing you would
   otherwise build: a state table, a job runner, a retry column, a
   reconciliation script, and someone on call for all of it.

If asked about Temporal Cloud versus self-hosted: the Server box is the only
thing that changes hands. Client and Workers are identical either way, which is
why moving between them is a connection string and a credential.

If asked "what happens if the Server goes down": running Workflows do not lose
state, because state is in the database, not in memory. Workers keep retrying
their polls and pick up where they left off. Nothing is lost; things just pause.
-->

---
layout: default
---

# Two kinds of code

<div class="kinds">
<div class="kind">
<p class="col-eyebrow">Workflow</p>
<p class="lede"><strong>Orchestrates the business logic.</strong> Sequencing, decisions, waiting. Never touches the outside world directly.</p>
<dl>
<dt>Determinism</dt><dd>⚠️ Must be deterministic</dd>
<dt>I/O</dt><dd>⚠️ No network, DB, or file I/O</dd>
<dt>Time</dt><dd><code>Workflow.currentTimeMillis()</code>, <code>Workflow.sleep()</code></dd>
<dt>Randomness</dt><dd><code>Workflow.newRandom()</code>, <code>Workflow.randomUUID()</code></dd>
<dt>Logging</dt><dd><code>Workflow.getLogger()</code></dd>
<dt>Retries</dt><dd>Off; failed Workflow Tasks retry</dd>
<dt>Duration</dt><dd>Indefinite</dd>
<dt>On failure</dt><dd><strong>Replays from history</strong></dd>
</dl>
</div>
<div class="kind">
<p class="col-eyebrow">Activity</p>
<p class="lede"><strong>Does the actual work</strong> that touches the outside world. An API call, a database write, sending an email.</p>
<dl>
<dt>Determinism</dt><dd>Can be non-deterministic</dd>
<dt>I/O</dt><dd>All I/O allowed</dd>
<dt>Time</dt><dd>Any clock</dd>
<dt>Randomness</dt><dd><code>new Random()</code>, <code>UUID.randomUUID()</code></dd>
<dt>Logging</dt><dd>Any SLF4J <code>Logger</code></dd>
<dt>Retries</dt><dd><code>RetryOptions</code> in <code>ActivityOptions</code></dd>
<dt>Duration</dt><dd>Typically short-lived</dd>
<dt>On failure</dt><dd><strong>Restarts from scratch</strong></dd>
</dl>
</div>
</div>

<style>
.kinds { display: grid; grid-template-columns: 1fr 1fr; gap: 1.2rem; margin-top: 0.2rem; }
.kind {
  padding: 0.9rem 1.1rem 1rem; border-radius: 0.7rem;
  border: 1px solid var(--temporal-border); background: var(--temporal-bg-elev);
}
.kind .lede { min-height: 4.2em; margin: 0 0 0.7rem !important; font-size: 0.95rem !important; line-height: 1.4 !important; }
.kind dl {
  display: grid; grid-template-columns: max-content 1fr; gap: 0.32rem 0.9rem;
  margin: 0; padding-top: 0.6rem; border-top: 1px solid var(--temporal-border);
}
.kind dt { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--temporal-text-muted); align-self: center; }
.kind dd { margin: 0; font-size: 0.82rem; color: var(--temporal-text); }
.kind dd code { font-size: 0.74rem !important; }
</style>

<!--
Source: PPT slide 24, "Workflow code cannot interact with the outside world
directly", merged with PPT slide 37 (`{{SLIDE:workflows-activities-comparison}}`),
the Workflows vs. Activities table that used to close the design-patterns
segment. The SDK names in the table are the Java ones.

This is the split everything else hangs on. Say the rule in one sentence and
come back to it every time a code slide appears: if it can fail for external
reasons, it is an Activity.

Do not read the rows out. Name the two that matter, Determinism and the last
row: "replays from history" versus "restarts from scratch" is the whole
difference in five words. The rest earns its keep in the PDF.

The Retries row, if asked. A Workflow Execution is not retried by default. You
can give it `setRetryOptions` in its `WorkflowOptions` when you start it, but
that is rare and usually a smell. What does retry is the Workflow Task: if the
Workflow throws an ordinary exception (a `NullPointerException`, an
`IllegalStateException`) because of a bug, Temporal retries the Workflow Task,
so you can fix the code and redeploy while the Workflow waits. Business
failures are handled in the Workflow's own code, or by throwing an
`ApplicationFailure` (`ApplicationFailure.newFailure(...)`), which fails the
Workflow. A retried Activity starts from the top, unless it heartbeats
progress.

One nuance if someone challenges the Time row: `Workflow.currentTimeMillis()`
and `Workflow.sleep()` *are* available inside a Workflow, and they are
deterministic because the SDK drives them from Workflow time rather than the
wall clock. It is `System.currentTimeMillis()` and `Thread.sleep()` that are
banned, not the concept of time. The same goes for the Logging row:
`Workflow.getLogger(MyWorkflowImpl.class)` is an SLF4J logger that stays quiet
during replay, so a replayed Workflow does not log every line twice.
-->

---
layout: default
---

# The Worker runs both

Workflow code runs under special constraints.

- **Workflow code must be deterministic.** Same inputs, same history, same
  decisions, every time it runs.
- **The Worker enforces this.** In Java, the SDK runs each Workflow on threads
  it schedules itself, one at a time, and **replays** it against its Event
  History: code that makes different decisions fails with a non-determinism
  error. So: `Workflow.sleep`, `Workflow.currentTimeMillis`,
  `Workflow.newRandom`, and no threads or I/O of your own.

<div v-click class="mt-8 text-2xl font-light">

If it can fail for external reasons, it's an Activity.

</div>

<!--
Source: PPT slide 25.

The Java SDK has no sandbox: your Workflow class is
ordinary bytecode, and nothing stops it calling `Thread.sleep` or opening a
socket. What it does instead is control how the code runs and check what it
does. Each Workflow Execution gets its own instance of your Workflow class,
run on Workflow threads that the SDK schedules deterministically, one at a
time. Every time the code asks for something durable (an Activity, a Timer, a
Child Workflow) the SDK records a command; on replay it compares those
commands with the Event History, and a mismatch fails the Workflow Task with
a `NonDeterministicException` instead of silently corrupting the run.

So the rules are yours to keep, and the SDK gives you a deterministic version
of each thing you would otherwise reach for: `Workflow.currentTimeMillis()`
for the clock, `Workflow.sleep()` for a durable Timer, `Workflow.newRandom()`
and `Workflow.randomUUID()` seeded from the run, `Async.function` and
`Promise` instead of threads or `CompletableFuture`, and
`Workflow.getLogger()` for logging. `System.currentTimeMillis()`,
`Thread.sleep()`, `new Thread()`, `synchronized`, static mutable state and any
I/O are out. The way to catch a slip before production is a replay test
against a recorded history; the versioning segment shows one.

That is also why the Workflow holds only the Activity interface. It needs the
Activities' signatures, never their code; the stub from
`Workflow.newActivityStub` turns each call into a scheduled Activity Task. It
appears on the Workflow anatomy slide shortly; plant it now.
-->

---
layout: code-walkthrough
heading: One Worker, both kinds of code
notes:
  - The one Task Queue this Worker polls for work
  - "Workflow code, by <strong>class</strong>: a new instance per Execution, run on the SDK's deterministic threads"
  - "Activity code, by <strong>instance</strong>: one plain Java object, free to touch the world"
  - Starts polling in the background. Nothing runs without a Worker
---

::code::

```java {all|2|3|4|5}
WorkerFactory factory = WorkerFactory.newInstance(client);
Worker worker = factory.newWorker("transfers");
worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
worker.registerActivitiesImplementations(new BankActivitiesImpl());
factory.start();
```

<!--
Code: snippets/src/main/java/io/temporal/devdays/essentials/concepts/WorkerRunsBoth.java.

The previous slide in code. One process, two very different ways of running
code, and you can see the split in the two `register` calls:

- **Build 1 - the Task Queue.** The Worker asks the Service for Tasks on
  `transfers`, and only those. The Client will start Workflows on the same
  name.
- **Build 2 - `registerWorkflowImplementationTypes`.** A class, not an object.
  The Worker creates a fresh instance for every Workflow Execution and runs it
  on its own Workflow threads. That is where the rules from the previous slide
  are checked.
- **Build 3 - `registerActivitiesImplementations`.** An object, called
  directly on the Worker's Activity threads, shared by every Activity Task.
  No rules: HTTP clients, databases, the file system. Because it is shared, it
  must be thread-safe, and it is the natural place to inject those clients.
- **Build 4 - `factory.start()`.** Starts the pollers on background threads
  and returns; they long-poll until the factory is shut down. The Server never
  runs your code; if no Worker is polling, Tasks wait on the queue.

Keep it short: the Java Workers section takes the whole file apart, the
`WorkflowServiceStubs` and `WorkflowClient` included. This slide is only about
the two halves.
-->
