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

In Temporal terms: `startToCloseTimeout` is how long Temporal waits before
deciding an Activity is dead. For a two-hour Activity, that means a crashed
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

- **Faster recovery:** instead of waiting out a 12-hour `startToCloseTimeout`
  before retrying a crashed job, a 30-second `heartbeatTimeout` means you
  notice and retry within seconds.
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
heading: Heartbeats in TypeScript
notes:
  - The Activity's execution info, from inside the Activity
  - Which attempt this is. 1 on the first try, 2 on the first retry
  - The last Heartbeat's saved details. Nothing on attempt 1, the progress so far on a retry
  - "The Heartbeat: <strong>still alive, still working</strong>, and here is how far I got"
---

::code::

```ts {all|2|3|4|9}
export async function packShipment(input: ShipmentInput): Promise<number> {
  const info = activityInfo();
  log.info(`attempt ${info.attempt}`);
  const start: number = info.heartbeatDetails ?? 0;

  for (let index = start; index < input.itemCount; index++) {
    await packItem(input.shipmentId, index);

    heartbeat(index + 1);
  }

  return input.itemCount;
}
```

<!--
Source: PPT slides 125-128. One build per slide in the original.

- **Build 1 - `activityInfo()`.** The original fetched an execution context
  object. In TypeScript the functions exported from `@temporalio/activity`
  (`activityInfo`, `heartbeat`, `log`) find the current Activity for you.
  `Context.current()` is the same thing, spelled longer.
- **Build 2 - the attempt.** Only logged here, but useful: an Activity can
  behave differently on a retry, for example by checking whether a charge
  already went through.
- **Build 3 - resume.** If this is:
  - attempt 1, `heartbeatDetails` is `undefined`, so `?? 0` starts from item 0
  - attempt 2 or later, start from wherever the previous attempt got to
- **Build 4 - the Heartbeat.** Tells Temporal "still alive" and records the
  progress. If the Worker crashes, the next attempt reads it back in build 3.

Heartbeats are throttled by the SDK, so calling it every item is fine; it will
not hit the Server every loop (at most once per 80% of the Heartbeat Timeout).
When the Activity throws, the SDK sends the latest details with the failure, so
a retry resumes exactly where the last attempt got to. If the whole Worker
crashes, the details the Server holds may be a little behind the last call, so
the work must tolerate redoing an item or two. Idempotent item processing, in
other words.

Two things the caller must do, or none of this works:

- set `heartbeatTimeout` in `proxyActivities`. Without it, Temporal is not
  watching for missed Heartbeats, and a dead Worker is only noticed when
  `startToCloseTimeout` expires.
- keep `startToCloseTimeout` long enough for the whole job. The Heartbeat
  Timeout is the short one.

`heartbeatDetails` holds whatever you passed, decoded by the Data Converter and
typed `any`. A number comes back as a number; an object comes back as a plain
object, not a class instance. Keep the details small: they are stored by the
Server like everything else.
-->

---
layout: code-walkthrough
heading: Heartbeating and cancellation
notes:
  - Heartbeat with progress, every item
  - A cancelled Activity finds out <strong>at a Heartbeat</strong>. The SDK aborts <code>cancellationSignal()</code>; look at it
  - Clean up on <code>CancelledFailure</code>, then rethrow so Temporal records the cancellation
---

::code::

```ts {all|5|6|9-12}
export async function processItems(input: ProcessInput): Promise<void> {
  try {
    for (const [index, item] of input.items.entries()) {
      await processItem(item);
      heartbeat(index + 1);
      cancellationSignal().throwIfAborted();
    }
  } catch (err) {
    if (err instanceof CancelledFailure) {
      await releaseLocks(input);
    }
    throw err;
  }
}
```

<!--
Source: PPT slides 129-130.

The original had a second, empty `heartbeat()` call "to check for
cancellation." TypeScript does not need one: when the Server tells the Worker
the Activity was cancelled, the SDK rejects the `cancelled()` Promise and aborts
`cancellationSignal()`, with a `CancelledFailure` as the reason. That delivery
rides on the Heartbeat, which is why an Activity that never heartbeats can never
be cancelled.

- **Build 1 -** progress on every item, as before.
- **Build 2 -** unlike some SDKs, TypeScript does not throw into the middle of
  your `await`. Your code has to look. `throwIfAborted()` throws the
  `CancelledFailure` once the signal is aborted. Passing the signal to anything
  that takes one (`fetch(url, { signal })`, a database driver) cancels the
  in-flight call too, and `sleep` from `@temporalio/activity` is already
  cancellation-aware.
- **Build 3 -** clean up and rethrow. Swallowing the `CancelledFailure` and
  returning normally makes the Activity look like it succeeded.

The caller decides how long to wait for this cleanup: with
`cancellationType: ActivityCancellationType.WAIT_CANCELLATION_COMPLETED` in
`proxyActivities`, the Workflow waits until the Activity has actually stopped.
-->
