---
slug: register-before-you-run
id: xcocahpxomdu
type: challenge
title: 3. Register Before You Run
teaser: The charge lands, then reports failure. The refund never runs. Move two lines
  to fix it.
notes:
- type: text
  contents: |-
    # What happens when the charge lands and still reports failure?

    A payment gateway captures the money, then times out before it answers. The
    card is charged. Your code is told it failed.

    If the refund is only added to the Saga after the charge succeeds, it is
    never added at all. The Saga can only run the undos it was given. The money
    is stranded.
tabs:
- id: 5xx84jpwpqru
  title: Exercise
  type: service
  hostname: workshop
  path: /?folder=/root/workshop/saga-order-fulfillment
  port: 8080
- id: xddtkzxmmwpr
  title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/saga-order-fulfillment/exercise
- id: iogfskyyxxya
  title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/saga-order-fulfillment/exercise
- id: jhaditzxhhte
  title: Temporal UI
  type: service
  hostname: workshop
  path: /
  port: 8233
difficulty: advanced
timelimit: 600
enhanced_loading: null
---

# Goal

Register each undo with the Saga **before** the step it undoes, so a step that
lands its effect and then fails is still reversed.

**Why it matters:** this is the failure that costs real money. The customer is
charged, the order fails, and nothing ever refunds them.

# See it first

Starting this Challenge stopped the Worker you ran in Challenge 2. Start a
fresh one in this Challenge's
[button label="Worker" background="#444CE7"](tab-1) tab:

```bash,run
run-worker
```

In the [button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter --charge-times-out
```

```bash,nocopy
Order failed: PaymentGatewayTimeout: payment gateway timed out after capturing 4999 cents
The Workflow ended Failed. That is expected: the order did not go through.

Event History of fulfillment-ORD-XXXXXX:
  steps completed: ReserveInventory
  step failed:     ChargePayment
  undos completed: ReleaseInventoryIfReserved
  undos still owed: RefundPaymentIfCharged
```

The Workflow ending Failed is expected: the charge failed, so the order did not
go through. The problem is the last line. The gateway captured 4999 cents
before it timed out, so the charge owes a refund, and **the refund never ran**.

The [button label="Worker" background="#444CE7"](tab-1) tab:

```bash,nocopy
[ledger] reserve:ORD-XXXXXX
[ledger] charge:ORD-XXXXXX
17:17:08 WARN OrderFulfillmentWorkflowImpl - fulfillment failed for ORD-XXXXXX, unwinding: PaymentGatewayTimeout: payment gateway timed out after capturing 4999 cents
[ledger] release:ORD-XXXXXX
```

The stock came back; the money did not. In the
[button label="Temporal UI" background="#444CE7"](tab-3) tab (click refresh if
the Workflow isn't listed), `ChargePayment` shows **Activity Task Failed**, and
the only undo after it is `ReleaseInventoryIfReserved`. There is no
`RefundPaymentIfCharged` event at all: the Workflow never knew it owed one.
(The history cannot show that the money moved either: the charge reports
Failed. The Client knows only because this scenario captures before it fails.)

The refund is added to the Saga on the line **after** the charge. When the
charge throws, that line never runs.

# Step 1 of 2: register the release first (TODO 2a)

**Observe.** Open `OrderFulfillmentWorkflowImpl.java` in the
[button label="Exercise" background="#444CE7"](tab-0) tab (`Ctrl+P`, then
`Ctrl+G` to jump to a line).

<!-- snippet: exercise/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2a -->
Lines 40-43 are the first step of `fulfill` and its undo:

```java
            // TODO 2a start: register the undo BEFORE the step, so swap these two statements
            activities.reserveInventory(order);
            saga.addCompensation(activities::releaseInventoryIfReserved, order);
            // TODO 2a end
```

**Learn.** `saga.addCompensation(...)` does not run anything. It pushes "call
`releaseInventoryIfReserved(order)`" onto the Saga's stack, for `compensate()`
to run later. Here the step runs first and its undo is registered after. If
`reserveInventory` holds the stock and then fails, the second line never runs,
so the unwind has no release to run. Registered first, the undo is on the stack
whatever the step does.

**Do.**

- [ ] Swap the two statements between the TODO 2a markers, so
  `saga.addCompensation(...)` comes first.

<details>
<summary>Show solution</summary>

<!-- snippet: solution/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2a -->
Lines 40-43 of `solution/.../OrderFulfillmentWorkflowImpl.java`:

```java
            // TODO 2a start: register the undo BEFORE the step, so swap these two statements
            saga.addCompensation(activities::releaseInventoryIfReserved, order);
            activities.reserveInventory(order);
            // TODO 2a end
```

</details>

# Step 2 of 2: register the refund first (TODO 2b)

**Observe.**

<!-- snippet: exercise/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2b -->
Lines 45-48, just below, are the charge and its undo:

```java
            // TODO 2b start: same swap for the charge
            activities.chargePayment(order);
            saga.addCompensation(activities::refundPaymentIfCharged, order);
            // TODO 2b end
```

**Learn.** The same gap, and it costs more here. If `chargePayment` charges the
card and then reports failure, the refund was never registered, and the
customer pays for an order that never ships.

**Do.**

- [ ] Swap the two statements between the TODO 2b markers.

<details>
<summary>Show solution</summary>

<!-- snippet: solution/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2b -->
Lines 45-48:

```java
            // TODO 2b start: same swap for the charge
            saga.addCompensation(activities::refundPaymentIfCharged, order);
            activities.chargePayment(order);
            // TODO 2b end
```

</details>

Leave TODO 3 in `OrderActivitiesImpl.java` alone; it is the next Challenge.

# Run it

Save, and the Worker you started in this Challenge restarts on its own: its tab
prints `Fulfillment Worker polling fulfillment-tasks` again. In the
[button label="Client" background="#444CE7"](tab-2) tab, run the same order
again:

```bash,run
run-starter --charge-times-out
```

```bash,nocopy
Order failed: PaymentGatewayTimeout: payment gateway timed out after capturing 4999 cents
The Workflow ended Failed. That is expected: the order did not go through.

Event History of fulfillment-ORD-XXXXXX:
  steps completed: ReserveInventory
  step failed:     ChargePayment
  undos completed: RefundPaymentIfCharged, ReleaseInventoryIfReserved
  undos still owed: none
```

The first line is the same as before the fix, word for word: the charge still
fails, and the Workflow still ends **Failed**, as it should. The difference is
below it: the refund completed, newest first, and nothing is still owed. The
[button label="Worker" background="#444CE7"](tab-1) tab now ends with
`[ledger] refund:ORD-XXXXXX` and then `[ledger] release:ORD-XXXXXX`.

The newest Workflow in the
[button label="Temporal UI" background="#444CE7"](tab-3) tab is the proof:

| Event | Activity |
|---|---|
| **Activity Task Completed** | `ReserveInventory` |
| **Activity Task Failed** | `ChargePayment` |
| **Activity Task Completed** | `RefundPaymentIfCharged` |
| **Activity Task Completed** | `ReleaseInventoryIfReserved` |
| **Workflow Execution Failed** | |

# Checkpoint

Click **Check**. It passes when a charge that lands and then fails is refunded,
and a reservation that lands and then fails is released. If it fails, the
message names TODO 2a or 2b.

---

**Please share your feedback so we can make better content for you.** The
**Feedback** tab takes a few seconds, and it is the only way we find out which
parts of this landed.
