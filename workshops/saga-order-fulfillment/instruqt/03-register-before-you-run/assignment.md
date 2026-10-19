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

If the [button label="Worker" background="#444CE7"](tab-1) tab is not running
the Worker, start it there:

```bash,run
run-worker
```

In the [button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter --charge-times-out
```

The [button label="Worker" background="#444CE7"](tab-1) tab:

```bash,nocopy
[ledger] reserve:ORD-XXXXXX
[ledger] charge:ORD-XXXXXX
17:17:08 WARN OrderFulfillmentWorkflowImpl - fulfillment failed for ORD-XXXXXX, unwinding: PaymentGatewayTimeout: payment gateway timed out after capturing 4999 cents
[ledger] release:ORD-XXXXXX
```

The stock came back. **The refund never ran.** In the
[button label="Temporal UI" background="#444CE7"](tab-3) tab (click refresh if
the Workflow isn't listed), `ChargePayment` shows **Activity Task Failed**, and
the only undo after it is `ReleaseInventoryIfReserved`. There is no
`RefundPaymentIfCharged` event at all: the Workflow never knew it owed one.

The refund is added to the Saga on the line **after** the charge. When the
charge throws, that line never runs.

# Where

<!-- snippet: exercise/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2a -->
Open `OrderFulfillmentWorkflowImpl.java` in the
[button label="Exercise" background="#444CE7"](tab-0) tab (`Ctrl+P`, then
`Ctrl+G` to jump to a line). Lines 37-40 look like this:

```java
            // TODO 2a start: register the undo BEFORE the step, so swap these two statements
            activities.reserveInventory(order);
            saga.addCompensation(activities::releaseInventoryIfReserved, order);
            // TODO 2a end
```

<!-- snippet: exercise/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2b -->
And lines 42-45, just below:

```java
            // TODO 2b start: same swap for the charge
            activities.chargePayment(order);
            saga.addCompensation(activities::refundPaymentIfCharged, order);
            // TODO 2b end
```

`saga.addCompensation(activities::releaseInventoryIfReserved, order)` does not
run anything. It pushes "call `releaseInventoryIfReserved(order)`" onto the
Saga's stack, for `compensate()` to run later.

# Change it

- [ ] **TODO 2a:** swap the two statements, so `saga.addCompensation(...)` comes
  before `activities.reserveInventory(order)`.
- [ ] **TODO 2b:** the same swap for the charge.

Change only the lines between each `start` and `end` marker. Leave TODO 3 in
`OrderActivitiesImpl.java` alone; it is the next Challenge.

<details>
<summary>Hint</summary>

Ask of each pair: if the first line throws, does the second one run? An undo
that is registered first is on the stack whatever the step does.

</details>

<details>
<summary>Show solution</summary>

<!-- snippet: solution/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2a -->
Lines 37-40 of `solution/.../OrderFulfillmentWorkflowImpl.java`:

```java
            // TODO 2a start: register the undo BEFORE the step, so swap these two statements
            saga.addCompensation(activities::releaseInventoryIfReserved, order);
            activities.reserveInventory(order);
            // TODO 2a end
```

<!-- snippet: solution/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 2b -->
Lines 42-45:

```java
            // TODO 2b start: same swap for the charge
            saga.addCompensation(activities::refundPaymentIfCharged, order);
            activities.chargePayment(order);
            // TODO 2b end
```

</details>

# Run it

Save, and the Worker restarts. Run the same order again:

```bash,run
run-starter --charge-times-out
```

```bash,nocopy
[ledger] reserve:ORD-XXXXXX
[ledger] charge:ORD-XXXXXX
17:16:52 WARN OrderFulfillmentWorkflowImpl - fulfillment failed for ORD-XXXXXX, unwinding: PaymentGatewayTimeout: payment gateway timed out after capturing 4999 cents
[ledger] refund:ORD-XXXXXX
[ledger] release:ORD-XXXXXX
```

The refund is back. The newest Workflow in the
[button label="Temporal UI" background="#444CE7"](tab-3) tab now has a
`RefundPaymentIfCharged` event after the failed charge.

# Checkpoint

Click **Check**. It passes when a charge that lands and then fails is refunded,
and a reservation that lands and then fails is released. If it fails, the
message names TODO 2a or 2b.
