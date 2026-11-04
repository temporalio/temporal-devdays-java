---
layout: section
---

<ZiggyHero scene="dodont">

# Patterns and Anti-Patterns

The shapes that hold up in production, and the ones that page you at night.

</ZiggyHero>

<!--
Source: docs.temporal.io/design-patterns, plus the limits in
docs.temporal.io/evaluate/cloud/limits.

A bank runs through the whole section, the same one as the Signals, Queries and
Updates segments: transfers, wires, deposits, accounts. Every pattern here is a
fix for a specific way a payment goes wrong.

Pacing: four pattern slides, then Resumable Activity and its demo at the end.
-->

---
layout: code-stack
heading: The business ID is the Workflow ID
proseMax: 30
---

::code::

```java
// ❌ A random ID: a double-tapped "Send" starts two transfers
TransferWorkflow random = client.newWorkflowStub(
    TransferWorkflow.class,
    WorkflowOptions.newBuilder()
        .setTaskQueue("payments")
        .setWorkflowId(UUID.randomUUID().toString())
        .build());
WorkflowClient.start(random::send, transfer);

// ✅ The transfer's own ID: the second start is rejected
TransferWorkflow byId = client.newWorkflowStub(
    TransferWorkflow.class,
    WorkflowOptions.newBuilder()
        .setTaskQueue("payments")
        .setWorkflowId("transfer-" + transfer.transferId())
        .build());
WorkflowClient.start(byId::send, transfer);
```

::default::

Two running Workflows can never share an ID. Make the ID the thing that must not
happen twice, and the second start throws `WorkflowExecutionAlreadyStarted`.

<!--
Source: docs.temporal.io/design-patterns/entity-workflow#best-practices and
temporal.io/blog/idempotency-and-durable-execution#idempotent-workflows.
Code: snippets/src/main/java/io/temporal/devdays/essentials/antipatterns/TransferStarts.java,
run by AntipatternsTest, which starts each version twice.

The ❌ version is the default most people write first, because the samples use
a UUID. A mobile app retries on a flaky connection, a user double-taps, a
load balancer replays a request: each one is a second transfer.

The ✅ version uses the ID the transfer already has. The blog's advice is to
generate that key as close to the user as possible: the app creates the
transfer ID when the user opens the form, not when the backend receives it.

Two policies to know by name, without reading them out:
- `setWorkflowIdConflictPolicy` is about a *running* Workflow with the same ID.
  The default fails the start; `WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING`
  attaches to the running one instead, which is what you want for "start it if
  it isn't already going".
- `setWorkflowIdReusePolicy` is about a *closed* one. The default,
  `ALLOW_DUPLICATE`, allows a new run with the same ID once the first has
  finished. For a transfer that must never run twice, set
  `WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE`; it holds for the Namespace's
  retention period.

Bonus: the transfer is now findable by its own ID in the Web UI and the CLI.
-->

---
layout: code-stack
heading: An idempotency key that survives retries
proseMax: 30
---

::code::

```java
@Override
public String postDebit(Debit debit) {
  ActivityInfo info = Activity.getExecutionContext().getInfo();
  String idempotencyKey = info.getWorkflowRunId() + "-" + info.getActivityId();

  return ledger.post(debit, idempotencyKey);
}
```

::default::

Activities run at least once. The run ID plus the Activity ID is the same on
every retry and unique across Workflows, so the ledger can drop the repeat.

<!--
Source: docs.temporal.io/activity-definition#idempotency and
docs.temporal.io/best-practices/error-handling#idempotence.
Code: snippets/src/main/java/io/temporal/devdays/essentials/antipatterns/DebitActivitiesImpl.java,
run by AntipatternsTest.

The failure this prevents: the Worker posts the debit, the ledger commits it,
and the Worker crashes (or the response times out) before Temporal hears about
it. Temporal has no record of success, so it retries. Without a key, the
customer is debited twice. The test does exactly this: the stub ledger commits
and then throws once, the retry sends the same key, and there is one posting.

The key is enforced by the service you call, not by the Activity. Most payment
APIs accept one (Stripe's `Idempotency-Key` header is the usual example). If
yours does not, an `operations` table with a unique constraint on the key does
the job.

`getWorkflowRunId()` is the run of the Workflow that scheduled the Activity. It
is `null` only for a Standalone Activity, which no Workflow scheduled; an
Activity called from a Workflow always has it. (The older `getRunId()` returns
the same value and is deprecated.)

Do not use `UUID.randomUUID()` inside the Activity: every attempt gets a new key,
which deduplicates nothing.
-->

---
layout: code-stack
heading: Polling an external system
proseMax: 30
---

::code::

```java
// ❌ Every pass adds an Activity and a Timer to history
String status = wires.checkSettlement(wireId);
while (!status.equals("SETTLED")) {
  Workflow.sleep(Duration.ofMinutes(1));
  status = wires.checkSettlement(wireId);
}

// ✅ One Activity. The Retry Policy does the waiting
WireActivities wires = Workflow.newActivityStub(
    WireActivities.class,
    ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(10))
        .setRetryOptions(RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofMinutes(1))
            .setBackoffCoefficient(1)
            .build())
        .build());
wires.awaitSettlement(wireId);
```

::default::

`awaitSettlement` checks once and throws if the wire hasn't settled. Retries are
not recorded in history, so it can poll for days and history stays one Activity.

<!--
Source: docs.temporal.io/design-patterns/polling#infrequent-polling-resource-efficient.
Code: snippets/src/main/java/io/temporal/devdays/essentials/antipatterns/LoopPollingWorkflowImpl.java
and RetryPollingWorkflowImpl.java, run by AntipatternsTest: four checks leave
four Activities and three Timers in the ❌ history, and one Activity in the ✅
one. In the ❌ half, `wires` is the same Activity stub, built once as a field.

The scenario: a wire has gone out on a payment network that has no webhook, and
the Workflow must wait for it to settle.

`setBackoffCoefficient(1)` is what turns retries into a fixed poll interval:
every wait is the Initial Interval. The Activity throws
`ApplicationFailure.newFailure(...)`, a retryable failure, when the wire is
still pending.

The docs split polling three ways, and it is worth naming the other two:
- Every second or faster: loop *inside* one Activity, heartbeating each pass.
- Every minute or slower: this slide.
- Several steps per poll, or parameters that change between polls: a Child
  Workflow that loops with `Workflow.sleep` and calls Continue-As-New.

If the network can call you back, skip polling: complete the Activity
asynchronously from the webhook.
-->

---
layout: code-stack
heading: Sleeping in an Activity
proseMax: 30
---

::code::

```java
// ❌ An Activity that waits out a three-day hold
@Override
public void releaseAfterHold(Deposit deposit) throws InterruptedException {
  Thread.sleep(Duration.ofDays(3));
  bank.release(deposit.depositId());
}

// ✅ A durable Timer in the Workflow, then a quick Activity
@Override
public void hold(Deposit deposit) {
  Workflow.sleep(Duration.ofDays(3));
  activities.releaseFunds(deposit.depositId());
}
```

::default::

The ❌ Activity holds a Worker slot for three days and starts over if the Worker
restarts. `Workflow.sleep` is a Timer on the Server: no slot, no restart.

<!--
Source: the Temporal docs on durable Timers in Java (`Workflow.sleep`).
Code: snippets/src/main/java/io/temporal/devdays/essentials/antipatterns/DepositActivitiesImpl.java
(❌) and DepositHoldWorkflowImpl.java (✅), run by AntipatternsTest on the
time-skipping test server: one Timer of exactly three days, then the release.

The scenario: a deposited cheque is held for three business days before the
funds are released.

Everything wrong with the ❌ version, in the order people discover it:
- It needs a Start-To-Close Timeout longer than three days.
- If the Worker restarts on day two, nothing notices until that timeout
  expires, and then the retry waits another three days from zero. A Heartbeat
  Timeout would notice sooner, but the retry still starts the wait over.
- It occupies an Activity slot, and a thread, the whole time, so a Worker can
  only hold as many deposits as it has slots.

The ✅ version costs two Events (TimerStarted, TimerFired), survives any number
of Worker restarts, and holds a million deposits as easily as one.
-->

---
layout: comparison
heading: "Resumable Activity (Pause On Failure)"
leftTitle: What it is
leftFeatures:
  - "An Activity's retries run out, and the Workflow parks instead of failing"
  - "It waits, using nothing, for a human to Signal in corrected input"
  - "Then it runs the same Activity again, in the same Workflow"
rightTitle: When to use it
rightFeatures:
  - "The failure is bad input someone can fix: a mistyped account number"
  - "An invalid reference, or a missing record that will be created"
  - "Restarting from scratch is worse: the customer would re-enter everything"
---

<!--
Source: docs.temporal.io/design-patterns/resumable-activity, overview and
problem statement.

The framing that lands: retrying can't fix bad input, because the same wrong
account number fails the same way every time. The usual options are both bad:
fail the Workflow (the customer re-enters everything), or mark the error
non-retryable (same thing). What you want is for the Workflow to pause, using
nothing, until someone with authority supplies the right value, and then pick
up exactly where it stopped.

When *not* to use it: transient failures (timeouts, 503s) are the Retry
Policy's job, and failures nobody can correct should fail fast with a
non-retryable error. And it is not the Approval pattern: approval gates
progress on a decision; this recovers from a failure with corrected data.

Next slide: the room plays the on-call engineer for exactly this scenario.
-->

---
layout: default
clicks: 4
---

# Resumable Activity: you're on call

<div class="rt-slide">
<ResumableTransfer />
</div>

<style>
.rt-slide {
  height: 22rem;
}
</style>

<!--
Source: docs.temporal.io/design-patterns/resumable-activity, and its best
practices.
Code: snippets/src/main/java/io/temporal/devdays/essentials/resumable/TransferWorkflowImpl.java
and TransferActivitiesImpl.java, run by ResumableTest; the console is
components/ResumableTransfer.vue.

Hand the room the keyboard, figuratively: "you're the engineer on call; what
do you do first?" Each click (arrow key or clicker) performs the next action,
in order. The buttons jump to an action if someone calls one out of order;
Reset goes back to the start.

The pattern in one breath: when an Activity fails because its input is wrong,
retries cannot fix it and failing the Workflow makes the customer start over.
So the Workflow catches the failure, parks on `Workflow.await`, and waits for a
Signal carrying the corrected input. Then it runs the same Activity again.

What each button teaches:
- **Send the transfer.** `executeTransfer` throws
  `ApplicationFailure.newNonRetryableFailure(..., "AccountNotFoundError")` for
  an unknown account, so there is one attempt, not the Retry Policy's three (it
  is `setMaximumAttempts(3)` for timeouts and 503s). The Workflow catches the
  `ActivityFailure` and is Running, not Failed. The Event History shows the
  Activity Type as `ExecuteTransfer`: the method name with its first letter
  capitalized, the Java SDK's default.
- **Query getStatus.** A `@QueryMethod` on the Workflow interface. The answer is `AWAITING_CORRECTION`, and the history
  does not grow. Point at that. In production, also set a Search Attribute with
  the status so operators can list every parked transfer, and alert when one
  parks rather than hoping someone opens the Web UI.
- **Signal "acct 1234".** `retryWithCorrection` is a `@SignalMethod`. The
  Signal is in history (Signals always are), but the handler checks the format
  and ignores it. A Signal handler cannot reject
  the way an Update validator can; if the operator needs an error back, make
  the correction an Update.
- **Signal "12345678".** The parked `Workflow.await` returns, the loop runs
  `executeTransfer` again, and the Workflow completes. Same run ID throughout:
  the test checks it.

Two rules worth one sentence each: catch only `ActivityFailure` (and rethrow it
when its cause is a `CanceledFailure`, so cancellation gets through), so a
Workflow bug is not parked as if it were bad data; and if a Workflow may be corrected many times,
Continue-As-New before the history grows.

This is not the Approval pattern: approval gates progress on a decision; this
recovers from a failure with corrected data. The docs' version adds an approval
Signal after the transfer, which is the Signals segment's pattern.

The live demo on the next slide is this exact scenario against a real Worker.
-->

---
layout: comparison
heading: "Resumable Activity in practice"
leftTitle: Key advantages
leftFeatures:
  - "The Workflow stays alive throughout: no restart from scratch"
  - "No external database, queue or polling mechanism"
  - "Status, corrected input and any approval are durably persisted the entire time"
rightTitle: Best practices
rightFeatures:
  - "Allow a few automatic retries before parking, for transient failures"
  - "Expose the status through a Query"
  - "Validate the corrected input in the Signal handler"
  - "Continue-As-New if many correction cycles are expected"
  - "Notify the operator (email, Slack, a ticket) when it parks"
---

<!--
Source: docs.temporal.io/design-patterns/resumable-activity, Key advantages and
Best practices.

One minute, and every row maps to something the room just clicked through:

- **Stays alive.** The status pill said Running the whole time; one run ID from
  start to finish.
- **No database or queue.** The parked transfer, its status and the corrected
  account live in the Workflow. Nothing to provision, nothing to reconcile.
- **Durably persisted.** Kill every Worker while it is parked: the Signal is
  still accepted and the transfer resumes when a Worker comes back.

- **Retries before parking.** `maximumAttempts: 3` handles a timeout or a 503;
  only the non-retryable `AccountNotFoundError` parks at once.
- **Query.** That was step 2. Pair it with a Search Attribute so operators can
  list every parked transfer, not just look one up.
- **Validate in the handler.** That was step 3, the "acct 1234" typo. If the
  operator needs an error back, make the correction an Update with a validator.
- **Continue-As-New.** Each correction cycle adds Events; a transfer corrected
  once never needs it, a long-lived record corrected weekly does.
- **Notify.** The transition to AWAITING_CORRECTION is the place to send the
  alert, rather than hoping someone opens the Web UI.
-->

---
layout: section
---

<ZiggyHero scene="demo">

# Demo: Resumable Activity

A transfer to a mistyped account, parked, corrected, and sent.

</ZiggyHero>

<!--
The code is the previous slide's, in snippets/ (TransferWorkflowImpl.java and
TransferActivitiesImpl.java under io/temporal/devdays/essentials/resumable).
The bank knows two accounts: 10000001 and 12345678.

Terminal 1, the dev server (skip if one is running):

    temporal server start-dev

Terminal 2, the Worker, from decks/java-master/snippets:

    mvn -q compile exec:java -Dexec.mainClass=io.temporal.devdays.essentials.resumable.ResumableDemo

Terminal 3, a transfer to an account that does not exist. The Workflow Type is
the interface name, `TransferWorkflow`:

    temporal workflow start --type TransferWorkflow \
      --task-queue resumable-demo --workflow-id transfer-4471 \
      --input '{"fromAccount":"10000001","toAccount":"99999999","amount":500}'

Open the Web UI at localhost:8233 and find `transfer-4471`. Show the room the
ActivityTaskFailed Event with `AccountNotFoundError`, then point out the
Workflow is still Running, not Failed.

Ask it where it is:

    temporal workflow query --workflow-id transfer-4471 --name getStatus

It answers `"AWAITING_CORRECTION"`. Optional: send a malformed fix and query
again to show the handler ignored it:

    temporal workflow signal --workflow-id transfer-4471 \
      --name retryWithCorrection --input '"acct 1234"'

Now the real fix:

    temporal workflow signal --workflow-id transfer-4471 \
      --name retryWithCorrection --input '"12345678"'

    temporal workflow result --workflow-id transfer-4471

It completes with "Transfer of 500 to 12345678 completed". Back in the Web UI,
the whole story is one history: the failure, the Signal, the second
ExecuteTransfer, the result.

To run it again, use a new --workflow-id, or wait for this one to close: the
default reuse policy allows a new run once the first has finished.
-->
