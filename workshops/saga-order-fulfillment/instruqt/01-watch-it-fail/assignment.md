---
slug: watch-it-fail
id: zigsnrfigb4j
type: challenge
title: 1. Watch It Fail
teaser: An order reserves stock and charges a card, then the courier fails. Watch
  nobody clean up.
notes:
- type: text
  contents: |-
    # Two steps went through. The third failed. What rolls them back?

    Placing an order takes three steps, one after another: hold the stock,
    charge the card, book the courier.

    When the courier step fails, the first two have already happened. The stock
    is held. The money is gone. There is no rollback, because no database
    transaction spans three separate systems.

    **Author:** [Nikolay Advolodkin](https://www.linkedin.com/in/nikolayadvolodkin/), Staff Developer Advocate
- type: text
  contents: |-
    # Four words for this lab

    **Saga.** A multi-step change across systems where every forward step has an
    undo, and the undos run when a later step fails.

    **Compensation.** One of those undos. Here: release the stock, refund the
    charge. In Temporal it is an ordinary Activity.

    **`Saga` class.** The Java SDK's helper for this, `io.temporal.workflow.Saga`.
    It holds a stack of compensations and runs them when you ask it to.

    **Idempotent.** Safe to run twice. Running it again changes nothing. Every
    compensation has to be, because Activities can be retried.
tabs:
- id: vsyt0vr2y1zs
  title: Exercise
  type: service
  hostname: workshop
  path: /?folder=/root/workshop/saga-order-fulfillment
  port: 8080
- id: ay9m5kl5gfzy
  title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/saga-order-fulfillment/exercise
- id: t4vtzihxnlvh
  title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/saga-order-fulfillment/exercise
- id: qs9fgsldijpg
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

Run an order that fails at the courier, and see that nothing undoes the first
two steps.

**Why it matters:** the customer has been charged for an order that will never
ship, and the stock is locked away from everyone else.

The blue buttons are clickable. Each one jumps to a tab.

# Where the code lives

The [button label="Exercise" background="#444CE7"](tab-0) tab opens
`saga-order-fulfillment` with two Maven projects:

- `exercise/` - **you work here.** Every path in this lab starts with `exercise/`.
- `solution/` - the same project, finished. `TODOS.md` in either one lists every TODO.

The code is in `src/main/java/io/temporal/devdays/saga/`. The Workflow is
`OrderFulfillmentWorkflowImpl.java`; the five Activities are in
`OrderActivitiesImpl.java`.

You change nothing in this Challenge. Just run it.

# Start the Worker

In the [button label="Worker" background="#444CE7"](tab-1) tab:

```bash,run
run-worker
```

It compiles for a few seconds, then prints:

```bash,nocopy
Fulfillment Worker polling fulfillment-tasks
```

Then it waits. Leave it running for the whole lab. Every time you save a Java
file it recompiles and restarts itself, and every `[ledger]` line appears here.

# Place an order that fails

In the [button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter --fail-dispatch
```

```bash,nocopy
Fulfilling ORD-XXXXXX...
Watch the Worker terminal for [ledger] lines.

Order failed: CourierUnavailable: no courier available for this route

Now read the ledger in the Worker terminal. A correctly
compensated order ends with the undos present, newest first:
  refund before release.
```

Now read the [button label="Worker" background="#444CE7"](tab-1) tab:

```bash,nocopy
[ledger] reserve:ORD-XXXXXX
[ledger] charge:ORD-XXXXXX
17:20:47 WARN OrderFulfillmentWorkflowImpl - fulfillment failed for ORD-XXXXXX, unwinding: CourierUnavailable: no courier available for this route
```

Stock held. Card charged. Courier failed. The Workflow even says it is
unwinding. **Nothing was undone.**

# Read the history

The ledger is something this lab made up. The Event History is real, and it is
the record you will have in production.

Open the [button label="Temporal UI" background="#444CE7"](tab-3) tab and click
the newest `fulfillment-ORD-...` Workflow. If it isn't in the list, click the
refresh button: the list does not always update on its own.

Read the Activity events from the top:

| Event | Activity |
|---|---|
| **Activity Task Completed** | `ReserveInventory` |
| **Activity Task Completed** | `ChargePayment` |
| **Activity Task Failed** | `DispatchCourier` |
| **Workflow Execution Failed** | |

What matters is what is **absent**: no `RefundPaymentIfCharged`, no
`ReleaseInventoryIfReserved`, anywhere in the history. After every run in this
lab, ask the same question: which Activities are here, and which should be here
and are not?

# Checkpoint

Click **Check**. It passes once a failed order is in the Temporal UI. Next you
make the undos run.
