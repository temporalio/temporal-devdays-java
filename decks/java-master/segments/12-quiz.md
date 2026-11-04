---
layout: section
---

<ZiggyHero scene="quiz">

# Quiz time!

</ZiggyHero>

<!--
Source: PPT slides 86-96.

Ten questions. Read each one, give the room ten seconds, take a show of hands
or a shout, then click to reveal. Every wrong answer that gets said aloud is a
teaching moment; ask why before you reveal.

If you are short on time, take questions 1, 4, 6 and 9. They cover the
Workflow/Activity split and the error model.
-->

---
layout: default
---

# 1. Which of the following is true about Workflow code?

- a) It can make HTTP calls directly to external services
- b) It must be deterministic and produce the same result on replay
- c) It runs on a separate server managed by Temporal Cloud
- d) It can start its own threads with `new Thread(...)` to run steps in parallel

<p v-click class="quiz-answer">Answer: <strong>b</strong></p>

<!--
Source: PPT slide 87.

Why the others are wrong: (a) HTTP belongs in an Activity. (c) Workflow code
runs in your Workers, never on the Server. (d) parallelism in a Workflow comes
from `Async.function` and `Promise`, which run on the SDK's own deterministic
threads. A thread you start yourself is invisible to replay.
-->

---
layout: default
---

# 2. What is the purpose of an Activity?

- a) To orchestrate the sequence of steps in a business process
- b) To store Workflow state between restarts
- c) To perform a single unit of work that can interact with external systems
- d) To route messages between Workflows, APIs, databases, or files

<p v-click class="quiz-answer">Answer: <strong>c</strong></p>

<!--
Source: PPT slide 88.

(a) is the Workflow's job. (b) is Event History's job; nobody stores Workflow
state by hand.
-->

---
layout: default
---

# 3. A Worker Process is responsible for:

- a) Storing Event History in the Temporal Server
- b) Polling Task Queues and executing Workflow and Activity code
- c) Scheduling new Workflow Executions on a Timer
- d) Managing connections between Temporal Cloud and your databases

<p v-click class="quiz-answer">Answer: <strong>b</strong></p>

<!--
Source: PPT slide 89.

(a) and (c) are the Server. (d) is nobody: Temporal never connects to your
databases. Your Activities do.
-->

---
layout: default
---

# 4. Java Workflow code must be deterministic. What does that mean in practice?

- a) Each Workflow runs in its own JVM, with full access to the Java standard library
- b) Use `Workflow.sleep`, `Workflow.currentTimeMillis()` and `Workflow.newRandom()` instead of `Thread.sleep`, `System.currentTimeMillis()` and `new Random()`, and do no I/O
- c) Workflow classes must be stateless: fields are lost on replay, so keep state in a database
- d) The Temporal Server runs Workflow code, so your Workers never execute it

<p v-click class="quiz-answer">Answer: <strong>b</strong></p>

<!--
Source: PPT slide 90, rewritten for the Java SDK.

The original asked why a Workflow cannot call an external API directly. The
answer is the same, determinism: a direct call could return something different
on replay and break history. In Java the rule is yours to keep: there is no
sandbox, and the compiler will not stop you calling `System.currentTimeMillis()`.
The `Workflow` class gives you deterministic replacements:
`Workflow.currentTimeMillis()` returns the time of the current Workflow Task,
`Workflow.newRandom()` is seeded per Workflow Execution, and `Workflow.sleep`
is a durable Timer. Break the rule and replay notices: the Worker throws a
`NonDeterministicException` and the Workflow Task retries until you fix the
code. Anything that touches the outside world, a file, a socket, an HTTP call,
goes in an Activity.

(a) is the Activity side: Activities are ordinary Java. (c) is the opposite of
the truth: fields are exactly where Workflow state lives, and replay rebuilds
them. (d) Workflow code only ever runs in your Workers.
-->

---
layout: default
---

# 5. What happens when a Worker crashes mid-Activity?

- a) The Workflow fails immediately and must be restarted manually
- b) Temporal marks the Workflow as timed out after 30 seconds
- c) Temporal reschedules the Activity on another available Worker
- d) The Activity result is lost and the Workflow skips to the next step

<p v-click class="quiz-answer">Answer: <strong>c</strong></p>

<!--
Source: PPT slide 91.

The nuance: Temporal notices when the Activity's Start-To-Close Timeout (or its
Heartbeat Timeout, if it heartbeats) expires. That is why the timeout matters,
and why long Activities should heartbeat: the heartbeating segment.
-->

---
layout: default
---

# 6. Which of these belongs in an Activity, not a Workflow?

- a) `activities.submitOrder(order)`, on a stub from `Workflow.newActivityStub`
- b) `Promise.allOf(task1, task2, task3).get()`
- c) `paymentService.charge(orderId, amount)`
- d) `Workflow.await(() -> approved)`

<p v-click class="quiz-answer">Answer: <strong>c</strong></p>

<!--
Source: PPT slide 92.

(a), (b) and (d) are all Workflow-safe: (a) schedules an Activity through
Temporal rather than calling it, and (b) waits on `io.temporal.workflow.Promise`,
not a `CompletableFuture`. (c) talks to the outside world. It might charge the
card twice on replay, or not at all.
-->

---
layout: default
---

# 7. `setStartToCloseTimeout` in an Activity's `ActivityOptions` controls:

- a) How long the entire Workflow may run before timing out
- b) How long a single attempt of the Activity may run before Temporal considers it failed
- c) How long Temporal waits before scheduling the first retry
- d) How long the Worker polls before giving up on a Task Queue

<p v-click class="quiz-answer">Answer: <strong>b</strong></p>

<!--
Source: PPT slide 93.

It resets on every retry. The deadline across all attempts is
`setScheduleToCloseTimeout`. (c) is `RetryOptions`' `setInitialInterval`.
-->

---
layout: default
---

# 8. Two Activities: an email validity check (fast, idempotent) and a payment charge (slow, external partner). Which is correct?

- a) Both should use identical Retry Policies for consistency
- b) The email check should have a longer timeout, because it's more critical
- c) The charge should have a higher `setMaximumAttempts`, because it costs more and fails more
- d) The charge should have a longer `setStartToCloseTimeout` and more conservative backoff

<p v-click class="quiz-answer">Answer: <strong>d</strong></p>

<!--
Source: PPT slide 94.

The point is that policy is per Activity, set by the Workflow: two
`Workflow.newActivityStub` calls with different `ActivityOptions`. (c) is tempting;
the answer is that more attempts on a slow external partner is how you get
rate limited. Give it time, not volume.
-->

---
layout: default
---

# 9. `ApplicationFailure.newNonRetryableFailure(...)` tells Temporal to:

- a) Retry the Activity immediately without waiting
- b) Skip the Activity and continue to the next step
- c) Stop retrying and fail the Activity permanently. It's a business rule violation, not a transient error
- d) Escalate the failure to a Parent Workflow

<p v-click class="quiz-answer">Answer: <strong>c</strong></p>

<!--
Source: PPT slide 95.

The Workflow still receives it, as an `ActivityFailure` whose `getCause()` is
the `ApplicationFailure`, and decides what to do. In the Web UI's timeline it shows
as one failed attempt and no retries.
-->

---
layout: default
---

# 10. A Temporal Worker is:

- a) A cloud process managed by Temporal that runs your code
- b) A process **you** run that hosts your Workflow and Activity code and polls a Task Queue
- c) A thread inside the Temporal Server that executes scheduled Workflows
- d) A load balancer that distributes work across Temporal Namespaces

<p v-click class="quiz-answer">Answer: <strong>b</strong></p>

<!--
Source: PPT slide 96.

If anyone picked (a) or (c), go back to the big picture slide for thirty
seconds. It is the most important misconception to clear before the lab.
-->

<style>
.quiz-answer {
  margin-top: 1.5rem;
  font-size: 1.3rem;
  color: var(--temporal-green);
}
</style>
