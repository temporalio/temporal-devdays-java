---
layout: eyebrow-hero
eyebrow: IMPLEMENTATION
---

# The order fulfillment Workflow

Register a compensation before or after each step, and run all compensations in
**reverse order** on failure.

<!--
Source: Temporal docs, Saga pattern page (Implementation).

Twenty seconds. The docs page shows a compact version of this Workflow. The
lab's version is the same shape with one thing the docs' own best practices ask
for: a failed compensation must not stop the ones after it. The Java SDK ships
a Saga helper, `io.temporal.workflow.Saga`, and the lab uses it:
`addCompensation` pushes one undo, `compensate()` runs them newest first. The
slide's version catches only `ActivityFailure`; the code slide says why.

The other way to write it: no helper, just a `Deque<Runnable>` in the Workflow,
`push` each undo, and a loop with its own try/catch that logs each failed
compensation. Same behaviour, more code, and you get to log every failure
yourself. The Saga helper swallows a failed compensation silently when
`continueWithError` is on; the failure still shows in the Event History as an
`ActivityTaskFailed` Event.
-->

---
layout: code-walkthrough
heading: Implementation
notes:
  - '<code>fulfill</code> implements the <code class="ref">@WorkflowMethod</code>: an <code>Order</code> in, an <code>OrderResult</code> out'
  - "<code>Saga</code> is the SDK's compensation stack, kept in Workflow state. Replay rebuilds it"
  - "Everything that can fail goes inside the <code>try</code>"
  - "Register the undo <strong>before</strong> the step. <code>addCompensation</code> stores it; nothing runs yet"
  - "Calling the stub runs <code>reserveInventory</code> as an Activity. Its undo is already on record"
  - "Same pair for the charge, so a charge that lands and then fails is still refunded"
  - 'Last step, no undo needed. No courier throws <code class="ref">CourierUnavailable</code>'
  - "Happy path: return the tracking number. The Saga is simply dropped"
  - "Catch <code>ActivityFailure</code> only. A bug in Workflow code retries instead of refunding"
  - '<code>log</code> comes from <code class="ref">Workflow.getLogger</code>, which is replay-aware'
  - "<code>saga.compensate()</code> runs the undos newest first: refund, then release"
  - "<code>setContinueWithError(true)</code>: a failed undo does not stop the ones after it"
  - "<code>throw e</code>: the original failure goes back to the caller. <strong>Compensating is not succeeding</strong>"
---

::code::

```java {all|1-2|3-4|6|7|8|10-11|13|14|15|16-17|18|4|19}
@Override
public OrderResult fulfill(Order order) {
  Saga saga =
      new Saga(new Saga.Options.Builder().setContinueWithError(true).build());

  try {
    saga.addCompensation(activities::releaseInventoryIfReserved, order);
    activities.reserveInventory(order);

    saga.addCompensation(activities::refundPaymentIfCharged, order);
    activities.chargePayment(order);

    String trackingNumber = activities.dispatchCourier(order);
    return new OrderResult(order.orderId(), trackingNumber);
  } catch (ActivityFailure e) {
    log.warn("fulfillment failed for {}, unwinding: {}",
        order.orderId(), Shared.rootCause(e));
    saga.compensate();
    throw e;
  }
}
```

<!--
Source: workshops/saga-order-fulfillment/solution/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java,
with the Workshop's `newSaga()` and `unwind()` helpers inlined (the Workshop
keeps them separate so TODO 1 has one place to edit).
Code: snippets/src/main/java/io/temporal/devdays/essentials/saga/OrderFulfillmentWorkflowImpl.java

Five minutes, one click per note. Out of frame, as fields of the class: one
`Workflow.newActivityStub(OrderActivities.class, ...)` hands back all five
Activities with a 10 second Start-To-Close Timeout and two attempts, and
`log` is `Workflow.getLogger(OrderFulfillmentWorkflowImpl.class)`. The SDK does
not know which of the five Activities is an undo.

The walk is in execution order: forward through the `try`, then the `catch` and
the unwind.

The bubbles are the short version. The full explanation for each build:

- **Build 1 - Lines 1-2: a Workflow definition.** `OrderFulfillmentWorkflow` is a `@WorkflowInterface` with one `@WorkflowMethod`, `fulfill`; this class implements it. The Worker registers the class, and a client starts it on the fulfillment-tasks Task Queue. It takes an Order and returns an OrderResult, both records; Temporal serializes them as JSON.

- **Build 2 - Lines 3-4: the compensation stack.** A `Saga` is a plain object, a list of undos, kept in Workflow state. If the Worker crashes, replay rebuilds it, so the Workflow still knows what it owes. The options come back on Build 12.

- **Build 3 - Line 6: the failure boundary.** Every step that can fail goes inside the try. A failing Activity lands in the catch below, which is where the unwind starts.

- **Build 4 - Line 7: register before the step.** `addCompensation` takes a method reference on the Activity stub and its argument, and stores them. Nothing runs yet.

- **Build 5 - Line 8: step 1 runs.** Calling a method on the Activity stub schedules reserveInventory on the Task Queue and blocks the Workflow until its result comes back. The reservation lands in the inventory system, outside the Workflow. Its undo is already on record.

- **Build 6 - Lines 10-11: the same pair for the charge.** Register refundPaymentIfCharged, then charge. Registering first means a charge that lands and then reports failure is still refunded.

- **Build 7 - Line 13: step 3 has no compensation.** dispatchCourier is last, so no later step can fail and leave it standing. When there is no courier, it throws a non-retryable ApplicationFailure of type CourierUnavailable, and the Workflow sees it as an ActivityFailure whose cause is that ApplicationFailure.

- **Build 8 - Line 14: the happy path.** If dispatch succeeds, the Workflow returns the tracking number. The Saga is simply dropped: nothing is undone.

- **Build 9 - Line 15: compensate Activity failures only.** An ActivityFailure is the business failure a Saga compensates. Anything else, such as a NullPointerException from a bug in Workflow code, is not caught, so not a single undo is scheduled. An exception that is not a Temporal failure fails the Workflow Task, not the Workflow, so Temporal retries the Task and you can fix and redeploy without refunding anyone. One thing this slide does not handle: if someone cancels the Workflow, the error is a CanceledFailure and it goes straight through. Compensating on cancellation means running the unwind in `Workflow.newDetachedCancellationScope`, since Activities scheduled in a cancelled scope are cancelled too.

- **Build 10 - Lines 16-17: log the failure.** The logger from `Workflow.getLogger` is replay-aware: it does not repeat this line when the Workflow replays its history. `Shared.rootCause` digs out the Activity's own failure, here CourierUnavailable.

- **Build 11 - Line 18: newest first.** `compensate()` walks its list from the end, so it runs refundPaymentIfCharged, then releaseInventoryIfReserved. The undo order mirrors the forward order. Each undo is an Activity, so it gets timeouts and retries too. By default it runs one undo at a time; `setParallelCompensation(true)` would start them all at once.

- **Build 12 - Line 4: one failure must not strand the rest.** `setContinueWithError(true)`: if one compensation fails after its retries, the Saga moves on to the next. The default is false, which stops the unwind at the first failed undo. The Saga does not log the failure for you; it shows in the Event History. In production, this is where you alert a human.

- **Build 13 - Line 19: re-throw.** `throw e` re-throws the original ActivityFailure, a Temporal failure, so the Workflow Execution ends Failed and the caller learns the order did not go through. Compensating is not succeeding.
-->

---
layout: default
---

# Implementation: the unwind

<div class="uw">
<div class="uw-code">

```java {all|1-4|7|8|10|11|13|15-18|18|18|19}
@Override
public OrderResult fulfill(Order order) {
  Saga saga =
      new Saga(new Saga.Options.Builder().setContinueWithError(true).build());

  try {
    saga.addCompensation(activities::releaseInventoryIfReserved, order);
    activities.reserveInventory(order);

    saga.addCompensation(activities::refundPaymentIfCharged, order);
    activities.chargePayment(order);

    String trackingNumber = activities.dispatchCourier(order);
    return new OrderResult(order.orderId(), trackingNumber);
  } catch (ActivityFailure e) {
    log.warn("fulfillment failed for {}, unwinding: {}",
        order.orderId(), Shared.rootCause(e));
    saga.compensate();
    throw e;
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

Three minutes, ten clicks, one order whose courier fails. The code is the same
block as the previous slide; this time watch the right-hand side.

- **Stack.** Each `addCompensation` puts an undo on top. Nothing on the stack
  runs until the courier fails. Then `compensate()` starts from the top: the
  refund, registered last, runs first. The Saga does not actually remove
  anything from its list; the picture shows what is still owed.
- **Event History.** This is what the Temporal UI shows for the same run, in
  order: the forward Activities and the failed `dispatchCourier` come first,
  the two compensation Activities come second, and `WorkflowExecutionFailed`
  closes it. Workflow Task Events are left out, as the UI's compact view does.

Open a real failed run in the Temporal UI after this slide, or in the lab, and
point at the same order of Events. The Workflow Type in the UI is
`OrderFulfillmentWorkflow`, the interface's name.
-->
