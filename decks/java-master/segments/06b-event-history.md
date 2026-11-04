---
layout: default
clicks: 9
---

# Inside one Workflow run

<WorkflowRun />

<!--
Back to concepts after the anatomy slides: the room has now seen
`Workflow.newActivityStub` and an Activity call, so this is what that call does
on the wire. One click per step; the line under the diagram
says what each step is, and the colours are the four kinds of thing in play.

1. **Worker action.** A Worker is running a Workflow Task, and your code hits
   `activities.chargeCard(...)`.
2. **Command.** The call becomes a `ScheduleActivityTask` Command. The Worker
   sends it to the Service when the Workflow Task finishes, not the moment the
   line runs.
3. **Event.** The Service records `ActivityTaskScheduled` in the Event History.
4. **Task.** The Service puts an Activity Task on the Task Queue.
5. **Worker action.** A Worker polls and takes the Activity Task. It may be a
   different Worker, on a different machine.
6. **Worker action.** The Worker runs `chargeCard`.
7. **Event.** On success, the Worker reports back and the Service records
   `ActivityTaskCompleted`. On failure, it records the failure or schedules a
   retry.
8. **Task.** The Service puts a new Workflow Task on the Task Queue.
9. **Worker action.** A Worker picks up that Workflow Task and replays the
   history (or, if it still has this Workflow in its cache, applies just the
   new Events); `activities.chargeCard(...)` now returns the recorded result,
   and the next line of your code runs.

Two details worth knowing. The Service writes `ActivityTaskStarted` only after
the attempt finishes, not at pickup, so a retrying Activity does not flood the
history with Started Events; that is why it appears at step 7, faded (the
runtime test for the next slide checks it sits directly before
`ActivityTaskCompleted`). And the
"next line runs" part depends on a second Workflow Task, not on the Activity
Worker handing control straight back.

Next: why the Service keeps that history, and then the same machinery with a
crash in the middle.
-->

---
layout: default
---

# Event History: why replay works

Every step of a Workflow is recorded as an Event. If a Worker dies, a new one
**replays** the history to rebuild the Workflow's state. Determinism is what
makes that safe, and it is why Workflow code has strict rules.

<div v-click class="mt-8 text-xl font-light">

Event History is like a `git log` for your Workflow Execution. You can always
reconstruct the current state by replaying from the beginning.

</div>

<!--
Source: PPT slide 26.

The previous slide showed the Event History being written, one Event per
step. This is why it is kept: it is what replay reads.

Make replay concrete: the Worker runs your Workflow method from the top. When
it reaches an Activity call, it does not run the Activity again. It finds the
`ActivityTaskCompleted` Event in history and resolves the call with the
recorded result. The method races forward through history to where it left
off, then carries on for real.

Which is exactly why reading anything from the outside world in Workflow code
is a bug: a config file, an environment variable, a database row. The replay
could take a different branch than the original run did, and the history would
no longer match the code. Temporal calls that a non-determinism error. The
same goes for the JDK's clock and random: `System.currentTimeMillis()` and
`new Random()` differ on replay. Use `Workflow.currentTimeMillis()` and
`Workflow.newRandom()`, which replay identically; the next table lists the
rest.
-->

---
layout: default
clicks: 8
---

# Event History and Replay

<ReplayFlow />

<!--
Source: docs.temporal.io/encyclopedia/event-history, whose example this
follows: a distance lookup, a 30-minute timer, a bill. It replaces the
"Putting it together" list from PPT slide 27.
Code: snippets/src/main/java/io/temporal/devdays/essentials/history/Pizza.java,
run and replayed by
snippets/src/test/java/io/temporal/devdays/essentials/history/EventHistoryTest.java,
which checks the Events below are the real history and that replay does not
call `getDistance` again.

One click per step. Clicks 1 and 2 separate the Command from the work: calling
`getDistance` through the Activity stub only asks the Service to schedule it;
the Activity runs on its own Activity Task, and only then are its Started and
Completed Events written. The beat to land is click 5: Worker B runs the same
code from the top, but the `getDistance` call does not send its Command. The
Worker finds `ActivityTaskScheduled` in the history, takes 15 from
`ActivityTaskCompleted`, and moves on. Activities are not re-executed during
replay; their results are read back.

Workflow Task events (`WorkflowTaskScheduled`, `Started`, `Completed`, and the
`WorkflowTaskTimedOut` that a crash produces) are left out to keep the history
readable. In the real history they sit between every pair of steps here. Also
left out: `ActivityTaskStarted` is written when the attempt closes, not when it
starts, because it carries the attempt count.

If someone asks what breaks this: replay compares each Command the code
produces with the next one the history expects. Branch on something read from
outside the Workflow, a config file or a database row, and replay may produce
`ScheduleActivityTask` where the history says `StartTimer`. That is a
non-determinism error (`NonDeterministicException`; the runtime test provokes
one with `WorkflowReplayer`). It fails the Workflow Task, not the Workflow, and
retries until you fix the code or version it.
-->

---
layout: default
---

# More replay-aware methods

Workflow-safe tools for the things you would normally reach for.

| In a Workflow, use                                    | For                                             |
| ----------------------------------------------------- | ----------------------------------------------- |
| `Workflow.sleep()`                                    | Waiting between checks. Durable across restarts |
| `Workflow.await()`                                    | Waiting for a Signal to flip a flag             |
| `Async.function()` + `Promise.allOf()`                | Running Activities in parallel                  |
| `Promise.anyOf()` `Workflow.newTimer()`               | Whichever finishes first                        |
| `Workflow.currentTimeMillis()` `Workflow.newRandom()` | Time and randomness that replay identically     |

<!--
Source: PPT slide 28.
Code: snippets/src/main/java/io/temporal/devdays/essentials/history/ReplayAwareTools.java
uses every row once; EventHistoryTest checks each does what the table says and
that the history replays cleanly.

In the Java SDK:

- `Workflow.sleep(Duration)` is a durable Timer. A Workflow can sleep for
  thirty days and it costs nothing while it waits. `Thread.sleep` in a
  Workflow is a bug: it blocks the Workflow thread and records nothing.
- `Workflow.await(() -> condition)` blocks until the condition is true. Give
  it a `Duration` first, `Workflow.await(timeout, () -> condition)`, and it
  returns `false` if time runs out first. You will see it again on the
  Signals slide.
- `Async.function(activities::price, sku)` starts an Activity without
  blocking and returns a `Promise`. `Promise.allOf(promises).get()` waits for
  all of them. Never use `CompletableFuture` or an `ExecutorService` in a
  Workflow; `Promise` is the replay-safe equivalent.
- `Promise.anyOf(...)` returns whichever completes first, and
  `Workflow.newTimer(Duration)` is a Timer as a `Promise`, so you can race an
  Activity against a deadline.
- `Workflow.currentTimeMillis()` returns the Workflow Task's time, and
  `Workflow.newRandom()` a `Random` seeded per run. `Workflow.randomUUID()`
  builds a UUID from that seed. `System.currentTimeMillis()`, `new Random()`
  and `UUID.randomUUID()` all compile in a Workflow and all break replay.
-->
