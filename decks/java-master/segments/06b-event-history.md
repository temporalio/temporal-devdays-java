---
layout: default
clicks: 9
---

# Inside one Workflow run

<WorkflowRun />

<!--
New to the TypeScript Deck. Back to concepts after the two anatomy slides:
the room has now seen `proxyActivities` and an Activity call, so this is what
that call does on the wire. One click per step; the line under the diagram
says what each step is, and the colours are the four kinds of thing in play.

1. **Worker action.** A Worker is running a Workflow Task, and your code hits
   `await chargeCard(...)`.
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
   history; `await chargeCard(...)` now resolves with the recorded result, and
   the next line of your code runs.

Two details worth knowing. The Service writes `ActivityTaskStarted` only after
the attempt finishes, not at pickup, so a retrying Activity does not flood the
history with Started Events; that is why it appears at step 7, faded. And the
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

Make replay concrete: the Worker runs your Workflow function from the top. When
it reaches an Activity call, it does not run the Activity again. It finds the
`ActivityTaskCompleted` Event in history and resolves the call with the
recorded result. The function races forward through history to where it left
off, then carries on for real.

Which is exactly why reading anything from the outside world in Workflow code
is a bug: a config file, an environment variable, a database row. The replay
could take a different branch than the original run did, and the history would
no longer match the code. Temporal calls that a non-determinism error. The SDK
covers the common traps for you (`Date.now()` and `Math.random()` are safe in a
Workflow), but not your own I/O.
-->

---
layout: default
clicks: 8
---

# Event History and Replay

<ReplayFlow />

<!--
Source: docs.temporal.io/encyclopedia/event-history, whose example this
follows: a distance lookup, a 30-minute timer, a bill. Ported from the Python
Deck, where it replaced the "Putting it together" list from PPT slide 27.

One click per step. Clicks 1 and 2 separate the Command from the work: calling
`getDistance` through `proxyActivities` only asks the Service to schedule it;
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
non-determinism error. It fails the Workflow Task, not the Workflow, and
retries until you fix the code or version it.
-->

---
layout: default
---

# More replay-aware methods

Workflow-safe tools for the things you would normally reach for.

| In a Workflow, use                     | For                                             |
| -------------------------------------- | ----------------------------------------------- |
| `sleep()`                              | Waiting between checks. Durable across restarts |
| `condition()`                          | Waiting for a Signal to flip a flag             |
| `Promise.all()`                        | Running Activities in parallel                  |
| `Promise.race()`                       | Whichever finishes first                        |
| `Date.now()` `Math.random()` `uuid4()` | Time and randomness that replay identically     |

<!--
Source: PPT slide 28.

The original listed `Workflow.sleep`, `Workflow.await`, `Workflow.allOf`,
`Workflow.anyOf` and `Workflow.newTimer`. The TypeScript mapping:

- `sleep()` from `@temporalio/workflow` is a durable Timer. A Workflow can
  sleep for thirty days and it costs nothing while it waits. A plain
  `setTimeout` inside a Workflow becomes the same Timer, but `sleep()` says
  what you mean.
- `condition()` resolves when the function you pass returns true. Give it a
  timeout as the second argument and it resolves `false` if time runs out
  first. You will see it again on the Signals slide.
- `Promise.all()` and `Promise.race()` are safe in a Workflow, because the SDK
  resolves Workflow promises deterministically, in the order history records.
- `Date.now()` and `new Date()` return the Workflow Task's time, and
  `Math.random()` is seeded per run. `uuid4()` from `@temporalio/workflow`
  builds a UUID from that seeded random. `crypto.randomUUID()` is not available
  in a Workflow at all.
- There is no separate "new Timer" API to learn. `sleep()`, or `condition()`
  with a timeout, covers it.
-->
