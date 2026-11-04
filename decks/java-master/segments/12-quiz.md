---
layout: section
---

<ZiggyHero scene="quiz">

# Quiz time!

</ZiggyHero>

<!--
Source: PPT slides 86-96. Ported from the Python Deck.

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
- d) It executes Activities synchronously, blocking until each completes

<p v-click class="quiz-answer">Answer: <strong>b</strong></p>

<!--
Source: PPT slide 87.

Why the others are wrong: (a) HTTP belongs in an Activity. (c) Workflow code
runs in your Workers, never on the Server. (d) it awaits Activities, but the
Workflow is suspended, not blocking a thread, and it can run several at once.
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

# 4. Workflow code runs in a deterministic V8 isolate. What does that mean?

- a) Each Workflow runs in its own Node.js process, with full access to Node APIs
- b) `Math.random()` and `Date.now()` are deterministic, and Node built-ins and network I/O are unavailable
- c) Workflow code must be written in plain JavaScript, not TypeScript
- d) The isolate runs on the Temporal Server, so your Workers never execute Workflow code

<p v-click class="quiz-answer">Answer: <strong>b</strong></p>

<!--
Source: PPT slide 90, rewritten for the TypeScript SDK.

The original asked why a Workflow cannot call an external API directly. The
answer is the same, determinism: a direct call could return something different
on replay and break history. In TypeScript the rule is enforced, not just
written down. The Worker bundles Workflow code and runs it in a V8 isolate
where `Math.random()` is seeded per Workflow Execution, `Date.now()` returns
the time of the current Workflow Task, and Node built-ins such as `fs` or
`http` cannot be imported. The fix for every one of these is the same: put it in
an Activity.

(a) is the Activity side: Activities are ordinary Node. (c) TypeScript compiles
away before the bundle runs. (d) Workflow code only ever runs in your Workers.
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

The nuance: Temporal notices when the Activity's `startToCloseTimeout` (or its
Heartbeat Timeout, if it heartbeats) expires. That is why the timeout matters,
and why long Activities should heartbeat: the heartbeating segment.
-->

---
layout: default
---

# 6. Which of these belongs in an Activity, not a Workflow?

- a) `await submitOrder(order)`, from `proxyActivities`
- b) `await Promise.all([task1, task2, task3])`
- c) `await paymentService.charge(orderId, amount)`
- d) `await condition(() => approved)`

<p v-click class="quiz-answer">Answer: <strong>c</strong></p>

<!--
Source: PPT slide 92.

(a), (b) and (d) are all Workflow-safe: (a) schedules an Activity through
Temporal rather than calling it. (c) talks to the outside world. It might
charge the card twice on replay, or not at all.
-->

---
layout: default
---

# 7. `startToCloseTimeout` on an Activity controls:

- a) How long the entire Workflow may run before timing out
- b) How long a single attempt of the Activity may run before Temporal considers it failed
- c) How long Temporal waits before scheduling the first retry
- d) How long the Worker polls before giving up on a Task Queue

<p v-click class="quiz-answer">Answer: <strong>b</strong></p>

<!--
Source: PPT slide 93.

It resets on every retry. The deadline across all attempts is
`scheduleToCloseTimeout`. (c) is the Retry Policy's `initialInterval`.
-->

---
layout: default
---

# 8. Two Activities: an email validity check (fast, idempotent) and a payment charge (slow, external partner). Which is correct?

- a) Both should use identical Retry Policies for consistency
- b) The email check should have a longer timeout, because it's more critical
- c) The charge should have a higher `maximumAttempts`, because it costs more and fails more
- d) The charge should have a longer `startToCloseTimeout` and more conservative backoff

<p v-click class="quiz-answer">Answer: <strong>d</strong></p>

<!--
Source: PPT slide 94.

The point is that policy is per Activity, set by the Workflow: two
`proxyActivities` calls with different options. (c) is tempting;
the answer is that more attempts on a slow external partner is how you get
rate limited. Give it time, not volume.
-->

---
layout: default
---

# 9. `ApplicationFailure.nonRetryable(...)` tells Temporal to:

- a) Retry the Activity immediately without waiting
- b) Skip the Activity and continue to the next step
- c) Stop retrying and fail the Activity permanently. It's a business rule violation, not a transient error
- d) Escalate the failure to a Parent Workflow

<p v-click class="quiz-answer">Answer: <strong>c</strong></p>

<!--
Source: PPT slide 95.

The Workflow still receives it, as an `ActivityFailure` whose `cause` is the
`ApplicationFailure`, and decides what to do. In the Web UI's timeline it shows
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
