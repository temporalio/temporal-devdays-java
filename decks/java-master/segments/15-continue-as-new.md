---
layout: section
---

<ZiggyHero scene="forever">

# Continue-As-New

Workflows that run forever, with a history that doesn't.

</ZiggyHero>

<!--
Source: PPT slides 113-120.
-->

---
layout: default
---

# Workflows that run forever

- Some processes genuinely never end: subscription billing, IoT polling,
  perpetual monitoring
- Temporal stores every Event in the Workflow's Event History
- An infinite Workflow creates infinite history
- Eventually: memory pressure, slow replay, and hard limits

<!--
Source: PPT slide 114.

A problem that only surfaces in production, which makes it easy to ignore in
development. A Workflow that runs fine for a week starts degrading after a
month.

The numbers: the Server warns at 10,240 Events or 10 MB of history, and
terminates the Workflow Execution once history passes 51,200 Events or 50 MB.
-->

---
layout: default
---

# What is Continue-As-New?

- Closes the current Workflow Execution and immediately starts a fresh one
- Same Workflow ID, same Workflow Type, **fresh history**
- The state you need to carry forward is passed as input to the new run
- From the outside, it looks like one continuous Workflow

<!--
Source: PPT slide 115.

The analogy that works: a software release. The application keeps running, but
the old version is swapped for a new one and users do not see the seam. Same
here. Observers see one Workflow ID that has been running for months; underneath
it has restarted dozens of times with a clean history each time.

Each run gets a new Run ID under the same Workflow ID. A Client stub created
from the Workflow ID alone follows the chain: `WorkflowStub.getResult(...)`
waits for the last run.
-->

---
layout: default
---

# When to use it

- Workflows that run indefinitely, or for very long periods
- Workflows that process unbounded queues or event streams
- Periodic Workflows that loop on a Timer
- Any Workflow approaching Temporal's history limits
- A clean point to adopt new code: the new run has no old history to replay

<!--
Source: PPT slide 116.

The Server stores the complete Event History for the lifetime of a Workflow
Execution. It logs a warning after 10,240 Events, and keeps warning as Events
are added. It terminates the Execution when history exceeds 51,200 Events,
contains more than 2,000 Updates, or contains more than 10,000 Signals.

The last bullet deserves a sentence. Replay always runs the Worker's current
code against the recorded history, which is why changing Workflow code can
cause non-determinism errors in long-running Workflows. A new run starts with
empty history, so there is nothing old to replay against the new code. (With
Worker Versioning, a Pinned run's continuation inherits its version unless you
ask for `AUTO_UPGRADE`.)
-->

---
layout: code-walkthrough
heading: "Continue-As-New in practice: a monthly subscription"
notes:
  - Bill every month for as long as the subscription lives. No end date
  - Charge the card, then count the cycle in <code>cyclesCompleted</code>, the state carried forward
  - A durable 30-day Timer. The Workflow costs nothing while it waits
  - Ask the SDK. <code>isContinueAsNewSuggested</code> turns true as this run's history nears its limits
  - Start a fresh run with an empty history and <strong>only</strong> the state it needs. <code>continueAsNew</code>
---

::code::

```java {all|8|9-10|11|13|14}
private final SubscriptionActivities activities = Workflow.newActivityStub(
    SubscriptionActivities.class,
    ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(5)).build());

@Override
public void run(SubscriptionInput input) {
  int cyclesCompleted = input.cyclesCompleted();
  while (true) {
    activities.chargeSubscription(input.subscriptionId(), cyclesCompleted + 1);
    cyclesCompleted++;
    Workflow.sleep(Duration.ofDays(30));

    if (Workflow.getInfo().isContinueAsNewSuggested()) {
      Workflow.continueAsNew(new SubscriptionInput(input.subscriptionId(), cyclesCompleted));
    }
  }
}
```

<!--
Source: PPT slide 118. The original was a status monitor that continued as new
on every loop and built a `newContinueAsNewStub`. This is the shape you would
actually ship: a subscription that bills every month for years, and stays in one
run until the Server says its history is getting big.
Code: snippets/src/main/java/io/temporal/devdays/essentials/continueasnew/SubscriptionWorkflowImpl.java,
run by ContinueAsNewTest. `SubscriptionInput`, `cyclesCompleted` and
`chargeSubscription` are the names the Continue-As-New Workshop uses.

- **Build 1 - the loop.** No end date. Without Continue-As-New, a subscription
  that runs for ten years builds one enormous history.
- **Build 2 - the charge.** An ordinary Activity with its own retries. The cycle
  counter starts from `input.cyclesCompleted()`, the input, which is why it
  survives the jump to a new run.
- **Build 3 - the Timer.** Two Events in history and nothing running for 30
  days; the Worker can evict the Workflow from its cache while it waits, which
  frees its Workflow thread.
  `Duration.ofDays(30)` keeps the slide simple; a real biller would compute the
  next calendar date from `Workflow.currentTimeMillis()`, the deterministic
  clock, never `System.currentTimeMillis()`.
- **Build 4 - the check.** `Workflow.getInfo().isContinueAsNewSuggested()` is
  the Server's hint, set well before the warning thresholds from two slides
  back: by default around 4K Events or 4 MB of history, or 90% of the Update
  limit. Call `Workflow.getInfo()` each time rather than caching the answer; the
  value changes as the run goes on. Checking it every month, at a safe point
  between charges, beats picking an arbitrary "every N months".
  (`getHistoryLength()` and `getHistorySize()` are there too if you want your
  own threshold.)
- **Build 5 - continue.** `Workflow.continueAsNew(...)` never returns. The SDK
  unwinds the Workflow thread to end the run (so never wrap it in a
  `catch (Throwable t)`), and the new run starts at the top of `run` with
  the new `SubscriptionInput` as its input: same Workflow ID, fresh history. For
  compile-time checking of the arguments, `Workflow.newContinueAsNewStub(SubscriptionWorkflow.class)`
  gives you a stub whose `run(...)` does the same thing.

How does it stop? Cancel the Workflow (`WorkflowStub.cancel()`, or
`temporal workflow cancel`). The cancellation reaches the 30-day
`Workflow.sleep` as a `CanceledFailure` and the subscription ends as Canceled;
catch it if you want a final "subscription closed" Activity, run inside
`Workflow.newDetachedCancellationScope(...)` so it is not cancelled too.

If the Workflow has Signal or Update handlers (a "change plan" Signal, say),
wait for them before continuing, or Continue-As-New cuts them off:
`Workflow.await(() -> Workflow.isEveryHandlerFinished())` just before
`continueAsNew`.
-->

---
layout: default
---

# What state to carry forward

- Only carry what you actually need. This is a fresh start, not a full backup
- State should be serializable and reasonably small
- **Good candidates:** counters, last processed ID, configuration, accumulated results
- **Bad candidates:** full Event History, large blobs. Use Claim Check instead

<!--
Source: PPT slide 119.

The design decision to think through. The input to the new run is your
checkpoint. Keep it lean: if your state interface grows without bound, you have
a different problem. Large state belongs in blob storage, with a reference
passed as the checkpoint.

It goes through the Data Converter like any Workflow argument, which by default
is Jackson JSON. A record or a plain class with a no-arg constructor and
getters round-trips; `java.time` types are supported; anything Jackson cannot
rebuild (an interface-typed field, a lambda, a live connection) does not belong
in it.
-->

---
layout: default
---

# What you got

- Truly perpetual Workflows without history bloat
- Clean, predictable memory use and replay performance
- Explicit state management at each continuation boundary
- Works with Child Workflows and Versioning

<!--
Source: PPT slide 120.

Close on the connections. Continue-As-New pairs naturally with Child Workflows
for long-lived parent orchestrators, and the state you carry forward needs the
same versioning discipline as your Workflow code. These patterns compose.
-->
