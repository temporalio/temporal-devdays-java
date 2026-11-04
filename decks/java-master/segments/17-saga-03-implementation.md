---
layout: eyebrow-hero
eyebrow: IMPLEMENTATION
---

# The order fulfillment Workflow

Register a compensation before or after each step, and run all compensations in
**reverse order** on failure.

<!--
Source: Temporal docs, Saga pattern page (Implementation). Ported from the Python Deck.

Twenty seconds. The docs page shows a compact version of this Workflow. The
lab's version is the same shape with two additions the docs' own best practices
ask for: a try/catch around each compensation, and a log line when one fails.
The TypeScript SDK has no Saga helper, so the compensation stack is ordinary
code: an array, a `for` loop and a `catch`. The slide's version also lets
anything but an `ActivityFailure` straight through; the code slide says why.
-->

---
layout: code-walkthrough
heading: Implementation
notes:
  - "An exported <code>async function</code> is the Workflow: an <code>Order</code> in, an <code>OrderResult</code> out"
  - "A plain array in Workflow state. Replay rebuilds it after a crash"
  - "Everything that can fail goes inside the <code>try</code>"
  - "Register the undo <strong>before</strong> the step. <code>unshift</code> stores it at the front; nothing runs yet"
  - "<code>await</code> runs <code>reserveInventory</code> as an Activity. Its undo is already on record"
  - "Same pair for the charge, so a charge that lands and then fails is still refunded"
  - 'Last step, no undo needed. No courier throws <code class="ref">CourierUnavailable</code>'
  - "Happy path: return the tracking number. The array is simply dropped"
  - "Compensate <code>ActivityFailure</code> only. A bug in Workflow code retries instead of refunding"
  - "<code>log</code> from the Workflow package is replay-aware"
  - 'A plain forward loop. <code class="ref">unshift</code> put the newest first: refund, then release'
  - "<code>await compensate()</code> calls the stored function, which runs the compensation Activity"
  - "Each undo has its own <code>catch</code>. One failure is logged, the rest still run"
  - "<code>throw err</code>: the original failure goes back to the caller. <strong>Compensating is not succeeding</strong>"
---

::code::

```ts {all|1-3|4|6|7|8|10-11|13|14|16|17|19|20-21|22-26|28}
export async function orderFulfillmentWorkflow(
  order: Order,
): Promise<OrderResult> {
  const compensations: Compensation[] = [];

  try {
    compensations.unshift(() => releaseInventoryIfReserved(order));
    await reserveInventory(order);

    compensations.unshift(() => refundPaymentIfCharged(order));
    await chargePayment(order);

    const trackingNumber = await dispatchCourier(order);
    return { orderId: order.orderId, trackingNumber };
  } catch (err) {
    if (!(err instanceof ActivityFailure)) throw err;
    log.error(`fulfillment failed for ${order.orderId}, unwinding`, { err });

    for (const compensate of compensations) {
      try {
        await compensate();
      } catch (compensationErr) {
        log.error(`compensation failed for ${order.orderId}`, {
          compensationErr,
        });
      }
    }
    throw err;
  }
}
```

<!--
Source: workshops/saga-order-fulfillment/solution/src/workflow.ts, lines 25-61,
comments trimmed, plus one line the Workshop does not have yet (line 16 here).
Ported from the Python Deck.

Five minutes, one click per note. Above the function, out of frame: one
`proxyActivities<typeof activities>` call hands back all five Activities with a
10 second Start-To-Close Timeout and two attempts, and
`type Compensation = () => Promise<void>` is the whole abstraction. The SDK does
not know which of the five Activities is an undo.

The walk is in execution order: forward through the `try`, then the `catch` and
the unwind.

The bubbles are the short version. The full explanation for each build:

- **Build 1 - Lines 1-3: a Workflow definition.** In TypeScript a Workflow is an exported async function in the Workflow module. The Worker bundles that module, and a client starts it by name on the fulfillment-tasks Task Queue. It takes an Order and returns an OrderResult; Temporal serializes both as JSON.

- **Build 2 - Line 4: the compensation array.** A plain local array, kept in Workflow state. If the Worker crashes, replay rebuilds it, so the Workflow still knows what it owes. Compensation is `() => Promise<void>`: a function with no arguments that returns a promise. The arrow functions close over `order`, which is why the type takes no arguments.

- **Build 3 - Line 6: the failure boundary.** Every step that can fail goes inside the try. A failing Activity lands in the catch below, which is where the unwind starts.

- **Build 4 - Line 7: register before the step.** `unshift` puts the undo at the front of the array before reserving. The arrow function is stored, not called, so nothing runs yet.

- **Build 5 - Line 8: step 1 runs.** `await` schedules reserveInventory on the Task Queue and waits for its result. The reservation lands in the inventory system, outside the Workflow. Its undo is already on record.

- **Build 6 - Lines 10-11: the same pair for the charge.** Register refundPaymentIfCharged, then charge. Registering first means a charge that lands and then reports failure is still refunded.

- **Build 7 - Line 13: step 3 has no compensation.** dispatchCourier is last, so no later step can fail and leave it standing. When there is no courier, it throws a non-retryable ApplicationFailure of type CourierUnavailable, and the Workflow sees it as an ActivityFailure whose `cause` is that ApplicationFailure.

- **Build 8 - Line 14: the happy path.** If dispatch succeeds, the Workflow returns the tracking number. The compensation array is simply dropped: nothing is undone.

- **Build 9 - Line 16: compensate Activity failures only.** An ActivityFailure is the business failure a Saga compensates. Anything else, such as a TypeError from a bug in Workflow code, is re-thrown before a single undo is scheduled. A plain Error thrown from a Workflow fails the Workflow Task, not the Workflow, so Temporal retries the Task and you can fix and redeploy without refunding anyone. The Workshop's code catches everything; without this line, the same bug would schedule real refunds and then leave the Workflow stuck retrying its Task. One thing this slide does not handle: if someone cancels the Workflow, the error is a CancelledFailure and it goes straight through. Compensating on cancellation means running the unwind inside `CancellationScope.nonCancellable`, since Activities scheduled in a cancelled scope are cancelled too.

- **Build 10 - Line 17: log the failure.** `log` from `@temporalio/workflow` is replay-aware: it does not repeat this line when the Workflow replays its history. The error's `cause` holds the Activity's own failure, here CourierUnavailable.

- **Build 11 - Line 19: newest first.** Because each undo was unshifted to the front, a plain forward `for...of` runs refundPaymentIfCharged, then releaseInventoryIfReserved. The undo order mirrors the forward order. A `push` with a forward loop would release the stock before refunding the card.

- **Build 12 - Lines 20-21: run one compensation.** `await compensate()` calls the stored arrow function, which schedules the compensation Activity and waits for it. Compensations are Activities, so they get timeouts and retries too.

- **Build 13 - Lines 22-26: one failure must not strand the rest.** Each compensation has its own try/catch. If one fails after its retries, it is logged and the loop moves on to the next. In production, this is where you alert a human.

- **Build 14 - Line 28: re-throw.** `throw err` re-throws the original ActivityFailure, a Temporal failure, so the Workflow Execution ends Failed and the caller learns the order did not go through. Compensating is not succeeding.
-->

---
layout: default
---

# Implementation: the unwind

<div class="uw">
<div class="uw-code">

```ts {all|1-4|7|8|10|11|13|15-17|19-21|19-21|28}
export async function orderFulfillmentWorkflow(
  order: Order,
): Promise<OrderResult> {
  const compensations: Compensation[] = [];

  try {
    compensations.unshift(() => releaseInventoryIfReserved(order));
    await reserveInventory(order);

    compensations.unshift(() => refundPaymentIfCharged(order));
    await chargePayment(order);

    const trackingNumber = await dispatchCourier(order);
    return { orderId: order.orderId, trackingNumber };
  } catch (err) {
    if (!(err instanceof ActivityFailure)) throw err;
    log.error(`fulfillment failed for ${order.orderId}, unwinding`, { err });

    for (const compensate of compensations) {
      try {
        await compensate();
      } catch (compensationErr) {
        log.error(`compensation failed for ${order.orderId}`, {
          compensationErr,
        });
      }
    }
    throw err;
  }
}
```

</div>
<SagaUnwind />
</div>

<style>
h1 { font-size: 1.8rem !important; margin-bottom: 0.5rem !important; }
.uw { display: grid; grid-template-columns: minmax(0, 1fr) 36%; gap: 1rem; height: 26.5rem; }
.uw-code { min-width: 0; overflow: hidden; }
.uw-code {
  --slidev-code-font-size: 0.6rem;
  --slidev-code-line-height: 0.86rem;
}
.uw-code :deep(pre.shiki) { margin: 0; padding: 0.55rem 0.7rem; }
.uw-code :deep(pre code), .uw-code :deep(pre .line) { font-size: 0.6rem !important; line-height: 0.86rem !important; }
</style>

<!--
Source: the same code as the previous slide, with components/SagaUnwind.vue.
Ported from the Python Deck.

Three minutes, ten clicks, one order whose courier fails. The code is the same
block as the previous slide; this time watch the right-hand side.

- **Stack.** Each `unshift` puts an undo on top. Nothing on the stack runs
  until the courier fails. Then the unwind starts from the top: the refund,
  registered last, runs first. The code does not actually remove anything from
  the array; the picture shows what is still owed.
- **Event History.** This is what the Temporal UI shows for the same run, in
  order: the forward Activities and the failed `dispatchCourier` come first,
  the two compensation Activities come second, and `WorkflowExecutionFailed`
  closes it. Workflow Task Events are left out, as the UI's compact view does.

Open a real failed run in the Temporal UI after this slide, or in the lab, and
point at the same order of Events.
-->
