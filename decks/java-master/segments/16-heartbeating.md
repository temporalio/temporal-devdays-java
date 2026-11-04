---
layout: section
---

<ZiggyHero scene="heartbeat">

# Heartbeats

Long-running Activities that say they're alive, and remember how far they got.

</ZiggyHero>

<!--
Source: PPT slides 122-131.
-->

---
layout: default
---

# Why Heartbeats?

Think of it like a contractor renovating your house.

- **Without Heartbeats,** you hand over the keys on Monday and agree to check
  back in two weeks. If they vanish on Tuesday, you won't know until the
  deadline, and you've lost nearly two weeks you could have spent hiring
  someone else.
- **With Heartbeats,** the contractor checks in every day. Miss a check-in and
  you know immediately, and can act.

<!--
Source: PPT slide 123.

The mental model: a Heartbeat is the Activity saying "I'm still alive and
making progress." If the Heartbeats stop, Temporal assumes the worst and acts.
You get visibility and automatic failure detection for long-running work.

In Temporal terms: the Start-To-Close Timeout (`setStartToCloseTimeout`) is how
long Temporal waits before deciding an Activity is dead. For a two-hour Activity, that means a crashed
Worker goes unnoticed for two hours. Heartbeats give you a two-hour Activity
with a thirty-second failure detection time.
-->

---
layout: default
---

# Business benefits

- **Faster failure recovery.** Less downtime, less customer impact
- **Resume from a checkpoint,** not from scratch
- **Cancellations are actually honoured**

<!--
Source: PPT slide 124.

- **Faster recovery:** instead of waiting out a 12-hour Start-To-Close Timeout
  before retrying a crashed job, a 30-second Heartbeat Timeout
  (`setHeartbeatTimeout`) means you notice and retry within seconds.
- **Resume from a checkpoint:** processing a million records and the Worker dies
  at 750,000? The Heartbeat details let the next attempt start at 750,001. That
  is real money saved in compute.
- **Cancellation:** if an order is cancelled mid-processing, the Heartbeat is
  how the cancellation reaches the running Activity. Without Heartbeats, the
  Activity keeps running and keeps consuming resources until it finishes on its
  own.
-->

---
layout: code-walkthrough
heading: Heartbeats in Java
notes:
  - The Activity's execution context, from inside the Activity
  - Which attempt this is. 1 on the first try, 2 on the first retry
  - The last Heartbeat's saved details. Empty on attempt 1, the progress so far on a retry
  - "The Heartbeat: <strong>still alive, still working</strong>, and here is how far I got"
---

::code::

```java {all|3|4|5|10}
@Override
public ShipmentResult packShipment(ShipmentInput input) {
  ActivityExecutionContext context = Activity.getExecutionContext();
  int attempt = context.getInfo().getAttempt();
  int start = context.getHeartbeatDetails(Integer.class).orElse(0);

  for (int item = start; item < input.itemCount(); item++) {
    packItem(input.shipmentId(), item);

    context.heartbeat(item + 1);
  }

  return new ShipmentResult(input.shipmentId(), input.itemCount(), attempt);
}
```

<!--
Source: PPT slides 125-128. One build per slide in the original.
Code: snippets/src/main/java/io/temporal/devdays/essentials/heartbeat/ShipmentActivitiesImpl.java,
run by HeartbeatTest. `packShipment`, `ShipmentInput` and `ShipmentResult` are
the names the Heartbeats Workshop uses.

- **Build 1 - the context.** `Activity.getExecutionContext()` finds the current
  Activity for you, from the thread the Activity runs on. Everything below
  hangs off it.
- **Build 2 - the attempt.** Returned in the result here, and worth logging:
  an Activity can behave differently on a retry, for example by checking
  whether a charge already went through.
- **Build 3 - resume.** `getHeartbeatDetails(Integer.class)` returns an
  `Optional`. If this is:
  - attempt 1, it is empty, so `orElse(0)` starts from item 0
  - attempt 2 or later, start from wherever the previous attempt got to
- **Build 4 - the Heartbeat.** Tells Temporal "still alive" and records the
  progress. If the Worker crashes, the next attempt reads it back in build 3.

Heartbeats are throttled by the SDK, so calling it every item is fine; it will
not hit the Server every loop. The Java SDK sends at most one Heartbeat per
throttle interval: 80% of the Heartbeat Timeout, capped at 60 seconds
(`WorkerOptions`' `maxHeartbeatThrottleInterval`), or 30 seconds when no
Heartbeat Timeout is set. When the Activity
throws, the SDK sends the latest details with the failure, so a retry resumes
exactly where the last attempt got to; the test proves it by failing on item 2
and checking that the retry starts at item 2. If the whole Worker crashes, the
details the Server holds may be a little behind the last call, so the work must
tolerate redoing an item or two. Idempotent item processing, in other words.

Two things the caller must do, or none of this works:

- set `setHeartbeatTimeout(...)` in the Workflow's `ActivityOptions`. Without
  it, Temporal is not watching for missed Heartbeats, and a dead Worker is only
  noticed when the Start-To-Close Timeout expires.
- keep the Start-To-Close Timeout long enough for the whole job. The Heartbeat
  Timeout is the short one.

The details hold whatever you passed, encoded by the Data Converter and decoded
into the class you ask for. `Integer.class` gives back an `Integer`; a record
comes back as that record. Keep the details small: they are stored by the
Server like everything else.
-->

---
layout: code-walkthrough
heading: Heartbeating and cancellation
notes:
  - Heartbeat with progress, every item. This call is also where cancellation arrives
  - A cancelled Activity finds out <strong>at a Heartbeat</strong>. The call throws <code>ActivityCanceledException</code>
  - Clean up, then rethrow so Temporal records the cancellation. <code>releaseLocks</code>
---

::code::

```java {all|7|9|10-11}
@Override
public void processItems(List<String> items) {
  ActivityExecutionContext context = Activity.getExecutionContext();
  try {
    for (int index = 0; index < items.size(); index++) {
      processItem(items.get(index));
      context.heartbeat(index + 1);
    }
  } catch (ActivityCanceledException e) {
    releaseLocks(items);
    throw e;
  }
}
```

<!--
Source: PPT slides 129-130.
Code: snippets/src/main/java/io/temporal/devdays/essentials/heartbeat/ProcessActivitiesImpl.java,
run by HeartbeatTest, which cancels the Workflow a second in and checks the
locks were released once.

The original had a second, empty `heartbeat()` call "to check for
cancellation." Java does not need one: the same `heartbeat(...)` call that
records progress is the one that throws. When the Server tells the Worker the
Activity was cancelled (the answer comes back on a Heartbeat), the next
`heartbeat(...)` call throws `ActivityCanceledException`. That delivery rides on
the Heartbeat, which is why an Activity that never heartbeats can never be
cancelled.

- **Build 1 -** progress on every item, as before.
- **Build 2 -** the SDK does not interrupt your thread. A blocking call in
  `processItem` runs to the end; the cancellation surfaces at the next
  Heartbeat. Keep the work between Heartbeats short, and remember throttling:
  with a 1-second Heartbeat Timeout the Worker talks to the Server about every
  0.8 seconds, so the cancellation lands within a second or so.
  `ActivityCanceledException` is one subclass of `ActivityCompletionException`;
  catch the parent instead to also clean up when the Activity is paused or
  reset, or the Worker is shutting down. The Heartbeats Workshop does that.
- **Build 3 -** clean up and rethrow. The SDK reports a thrown
  `ActivityCanceledException` as a cancellation. Swallowing it and returning
  normally makes the Activity look like it succeeded.

The caller decides how long to wait for this cleanup: with
`setCancellationType(ActivityCancellationType.WAIT_CANCELLATION_COMPLETED)` in
the Workflow's `ActivityOptions`, the Workflow waits until the Activity has
actually stopped. The default, `TRY_CANCEL`, requests the cancellation and
moves on without waiting.
-->
