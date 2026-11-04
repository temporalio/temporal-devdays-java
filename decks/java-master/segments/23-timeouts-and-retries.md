---
layout: section
---

<ZiggyHero scene="timeout">

# Timeouts and Retry Policies

Detect failures with timeouts, then mitigate them with automatic retries.

</ZiggyHero>

<!--
Source: docs.temporal.io/evaluate/features/timeouts-and-retries (timeouts)
and docs.temporal.io/encyclopedia/retry-policies (Retry Policies). Ported from
the Python Deck. The diagrams follow the sequence-diagram style of
docs.temporal.io/design-patterns/fixed-count-retries, stepped one click at a
time, with the Event History each scenario actually produces.

The idea to land first: Temporal cannot see a crash, a hang, or a network
partition. A timeout is how it notices. A retry is what it does about it.
-->

---
layout: default
---

# Workflow timeouts

<div class="set-in">

Set by the **Client**, when it starts the Workflow:

```ts
await client.workflow.start(orderWorkflow, {
  args: [order],
  taskQueue,
  workflowId,
  workflowExecutionTimeout: "30 days",
});
```

</div>

| Timeout                        | Bounds                                                              | Default        | Option                     |
| ------------------------------ | ------------------------------------------------------------------- | -------------- | -------------------------- |
| **Workflow Execution Timeout** | The whole Workflow Execution, including retries and Continue-As-New | Infinite       | `workflowExecutionTimeout` |
| **Workflow Run Timeout**       | A single Workflow Run, excluding retries and Continue-As-New        | Infinite       | `workflowRunTimeout`       |
| **Workflow Task Timeout**      | One Workflow Task, once a Worker has picked it up                   | 10s (max 120s) | `workflowTaskTimeout`      |

Most applications set **none of these**. For time-based logic, use a **Timer**.

<style>
.set-in p { margin: 0 0 0.3rem; font-size: 1rem; }
.set-in pre, .set-in pre code { font-size: 0.72rem !important; }
</style>

<!--
Source: docs.temporal.io/evaluate/features/timeouts-and-retries#workflow-timeouts.

Workflows are built to run for a long time and survive failures, which is why
most applications set none of these. The docs' own guidance: Workflow Execution and Run Timeouts mostly exist to
bound Temporal Cron Jobs. A Workflow Execution Timeout on a business process
means that when it fires, the Workflow just stops, without running any cleanup
code. If you want "give up after 30 days", write it in the Workflow with a
Timer and handle it.

In TypeScript they are `workflowExecutionTimeout`, `workflowRunTimeout` and
`workflowTaskTimeout` in the options of `client.workflow.start(...)` (or
`execute`), as a duration string or milliseconds. Not on the Workflow function:
the caller decides, as with Activities. `taskQueue` and `workflowId` are the two
options every start needs. The Run Timeout's docs default is "same as
Execution", which is infinite unless you set it.

The Workflow Task Timeout is the one that matters in practice, and nobody
changes it. The next slide shows why it exists.
-->

---
layout: default
clicks: 7
---

# Workflow Task Timeout

<TimeoutSequence scenario="workflow-task" />

<!--
Source: docs.temporal.io/evaluate/features/timeouts-and-retries#workflow-timeouts
("the maximum time a Worker has to execute a Workflow Task after picking it up
from the Task Queue").

Step through it. The point is that Workflow Tasks retry on their own, forever:
a Retry Policy does not apply to them. The second Worker replays the history to
rebuild the Workflow's state, then carries on from where the first one died.

The history on the right is the real sequence. After WorkflowTaskTimedOut,
the retry is a "transient" Workflow Task: its Scheduled and Started Events are
written only once it completes, all three together, with `attempt: 2` on
Scheduled. If you see a lot of WorkflowTaskTimedOut in
production, Workers are dying or blocked, not timing out by design.

Not the same thing as the TypeScript Worker's own limit: Workflow code that
runs for 5 seconds without yielding (a busy loop, say) is stopped by the Worker,
and that Workflow Task fails. That shows up as WorkflowTaskFailed, not TimedOut.
-->

---
layout: default
---

# Activity timeouts

<div class="set-in">

Set by the **Workflow**, on the proxy it calls the Activity through:

```ts
const { chargePayment } = proxyActivities<typeof activities>({
  startToCloseTimeout: "10 seconds",
});
```

</div>

| Timeout                       | Bounds                                                     | Default                 | Option                   |
| ----------------------------- | ---------------------------------------------------------- | ----------------------- | ------------------------ |
| **Schedule-To-Start Timeout** | Time an Activity Task waits in the Task Queue for a Worker | Infinite; never retried | `scheduleToStartTimeout` |
| **Start-To-Close Timeout**    | One attempt, from a Worker picking it up to its result     | Infinite                | `startToCloseTimeout`    |
| **Schedule-To-Close Timeout** | The whole Activity Execution: every attempt and every wait | Infinite                | `scheduleToCloseTimeout` |
| **Heartbeat Timeout**         | The gap between two Heartbeats from a running attempt      | None                    | `heartbeatTimeout`       |

Every Activity needs **Start-To-Close or Schedule-To-Close**. Set Start-To-Close:
the Temporal Service cannot see a Worker crash, and this is the timeout that
bounds each attempt.

<style>
.set-in p { margin: 0 0 0.3rem; font-size: 1rem; }
.set-in pre, .set-in pre code { font-size: 0.72rem !important; }
.set-in + table, .slidev-layout table { margin-top: 0.2rem; }
.slidev-layout td { padding-top: 0.35rem; padding-bottom: 0.35rem; }
</style>

<!--
Source: docs.temporal.io/evaluate/features/timeouts-and-retries#activity-timeouts.

All four are options to `proxyActivities(...)` in the Workflow: the caller sets
them, not the Activity. Every Activity called through that proxy shares them;
make a second proxy for Activities that need different values. The Activity's
own code never sets its timeouts.

On the Start-To-Close default: the docs say it defaults to the Schedule-To-Close
Timeout, which is infinite unless you set it. So if you set only
Schedule-To-Close, each attempt inherits that value; set neither and the SDK
refuses: `proxyActivities` itself throws a `TypeError` in the Workflow,
which fails the Workflow Task until you fix the code.

The next four slides take each in turn. Ask the room to guess, before each
one, which events end up in history. Almost everyone expects to see every
failed attempt. They do not appear, and that is the surprise worth teaching.
-->

---
layout: default
clicks: 8
---

# Start-To-Close Timeout

<TimeoutSequence scenario="start-to-close" />

<!--
Source: docs.temporal.io/evaluate/features/timeouts-and-retries#activity-timeouts.

The one timeout you must understand. It bounds a single attempt and resets on
every retry.

Step 4 is the sentence to say out loud: the Service has no way to see that a
Worker died. It only knows an attempt started and no result came back.
Start-To-Close is how it turns "no news" into "that attempt failed".

Watch the Event History panel at step 7: ActivityTaskStarted appears only now,
with attempt 2. A pending Activity's attempts are not written to history until
it closes; to see a pending Activity's attempt count live, describe the
Workflow (`temporal workflow describe`) or look at the Pending Activities view
in the UI.

Size it to the slowest healthy attempt, not to the whole job.
-->

---
layout: default
clicks: 6
---

# Schedule-To-Start Timeout

<TimeoutSequence scenario="schedule-to-start" />

<!--
Source: docs.temporal.io/evaluate/features/timeouts-and-retries#activity-timeouts.

Rarely set, and the docs say why: it is infinite by default and is never
retried. A task waiting in the queue is a capacity problem (not enough Workers,
or Workers polling the wrong Task Queue name), and retrying the Activity
cannot fix capacity.

Where it does earn its place: routing. If a task waits too long on one Task
Queue, the Workflow can catch the `ActivityFailure`, see a `TimeoutFailure`
cause, and send the work somewhere else.

The usual signal instead is the Activity schedule-to-start latency metric
(`temporal_activity_schedule_to_start_latency`) (covered in the Observability segment),
alerted on, rather than a timeout that fails the work.
-->

---
layout: default
clicks: 8
---

# Heartbeat Timeout

<TimeoutSequence scenario="heartbeat" />

<!--
Source: docs.temporal.io/evaluate/features/timeouts-and-retries#activity-timeouts.

Why long Activities need Heartbeats. With only a one-hour Start-To-Close, a hung
Worker costs you up to an hour. A 30-second Heartbeat Timeout notices in 30
seconds.

Two things happen on the retry, both visible in the diagram: the attempt is
retried, and it gets the last heartbeat details (`[200]`, read back with
`activityInfo().heartbeatDetails`), so it resumes rather than starting over.
That is the Heartbeating segment's code, seen from the Service's side.

The Start-To-Close bar goes grey with "had ~59m left": it never fired. The
Heartbeat Timeout got there first.
-->

---
layout: default
clicks: 8
---

# Schedule-To-Close Timeout

<TimeoutSequence scenario="schedule-to-close" />

<!--
Source: docs.temporal.io/evaluate/features/timeouts-and-retries#activity-timeouts.

The budget for the whole Activity, and the right way to bound retries. It
starts when the Activity is scheduled, so it also covers time spent waiting in
the Task Queue and every backoff between attempts.

The docs' recommendation, and the line to end on: limit retries with
Schedule-To-Close, not with Maximum Attempts. "Give up after 10 seconds" is a
promise a caller can plan around. "Give up after 3 attempts" takes anywhere
from milliseconds to hours, depending on the intervals.

The final event is ActivityTaskTimedOut with timeout type SCHEDULE_TO_CLOSE.
The failed attempts before it are not events.
-->

---
layout: default
---

# Retry Policies

A Retry Policy tells Temporal how and when to try again after a failure.

<div class="cols-2">
<div>
<p class="col-eyebrow">Default for every Activity</p>

| Property             | Default                |
| -------------------- | ---------------------- |
| Initial Interval     | 1 second               |
| Backoff Coefficient  | 2.0                    |
| Maximum Interval     | 100 × Initial Interval |
| Maximum Attempts     | Unlimited              |
| Non-Retryable Errors | None                   |

</div>
<div>
<p class="col-eyebrow">Activities vs Workflows</p>

- **Activities retry by default:** that is where transient failures happen
- **Workflows do not:** a retry replays the same Events, so retry the
  Activities inside instead
- **Workflow Tasks** retry on their own, outside any Retry Policy

</div>
</div>

<!--
Source: docs.temporal.io/encyclopedia/retry-policies#default-behavior and
#properties.

The TypeScript shape, with the defaults spelled out, is the `retry` option of
`proxyActivities`:

```ts
retry: {
  initialInterval: "1 second",
  backoffCoefficient: 2,
  maximumInterval: "100 seconds",
  // maximumAttempts: leave it unset for unlimited; 1 means no retries
  nonRetryableErrorTypes: ["CardDeclined"],
}
```

In TypeScript, unlimited means leaving `maximumAttempts` out (or `Infinity`).
The SDK rejects `0`, which other SDKs and the server read as unlimited.

Maximum Attempts of 1 means no retries at all, and is the setting people reach
for when they mean "don't do this twice". That is an idempotency problem, not a
retry problem: a single network blip or Worker restart then fails the Activity.

A Workflow can be given a Retry Policy when it is started (`retry` in the
`client.workflow.start` options). The docs recommend it only for Temporal Cron
Jobs and stateless, always-running Workflows.
-->

---
layout: default
clicks: 11
---

# Maximum Attempts

<TimeoutSequence scenario="max-attempts" />

<!--
Source: docs.temporal.io/design-patterns/fixed-count-retries, the same scenario
as its diagram: three attempts against a failing Payment API.

Read the waits as you click: 1s is the Initial Interval, 2s is the Backoff
Coefficient doubling it. With the default unlimited Maximum Attempts, the
fourth retry would wait 4s and so on, up to 100s, forever.

The final event is ActivityTaskFailed with retry state MAXIMUM_ATTEMPTS_REACHED,
and it carries the last attempt's error. Again, attempts 1 and 2 are not in
history. In the Workflow, the `ActivityFailure` has `retryState`
`"MAXIMUM_ATTEMPTS_REACHED"`, and its `cause` is the last attempt's error, as an
`ApplicationFailure`.

When a fixed count is right: each attempt costs money or a rate-limited token.
Otherwise prefer Schedule-To-Close (two slides back).
-->

---
layout: default
clicks: 5
---

# Non-retryable errors

<TimeoutSequence scenario="non-retryable" />

<!--
Source: docs.temporal.io/encyclopedia/retry-policies#non-retryable-errors.

A permanent failure: no number of retries will un-decline a card. Two ways to
stop retrying, and the docs frame it as implementer versus caller:

- **The Activity decides:**
  `throw ApplicationFailure.create({ type: "CardDeclined", nonRetryable: true })`,
  as on the slide. Use it when the error is always permanent, whoever calls.
- **The caller decides:** `retry: { nonRetryableErrorTypes: ["CardDeclined"] }`
  in the Workflow's `proxyActivities` options. It matches the error's `type`.
  Use it when only this Workflow knows the error is permanent.

The docs say to use them sparingly: invalid input is the classic case. The
Workflow gets an `ActivityFailure` whose `cause` is an `ApplicationFailure` with
`type` `"CardDeclined"`, which is the Error handling segment's next slide.
-->

---
layout: default
---

# Retries in Event History

- **A pending Activity's attempts are not events.** `ActivityTaskStarted` is
  written only when the Activity closes, with the final attempt's number
- **To watch attempts live,** describe the Workflow: the Pending Activities
  view shows the attempt count and the last failure
- **A retried Workflow** ends its run with `WorkflowExecutionFailed`
  (`retryState=IN_PROGRESS`) and starts a new run, whose first Workflow Task waits
  out the backoff

<!--
Source: docs.temporal.io/encyclopedia/retry-policies#event-history.

This is why the diagrams' Event History panels stay so short. The design keeps
history from filling up with noise during a long outage: an Activity retrying
every 100 seconds for a day would otherwise add thousands of Events.

The practical consequence for debugging: "the history shows one attempt" does
not mean there was one. Look at the `attempt` field on ActivityTaskStarted, or
at Pending Activities while it is still running.

`temporal workflow describe --workflow-id <id>` prints Pending Activities with
their attempt count and last failure.
-->
