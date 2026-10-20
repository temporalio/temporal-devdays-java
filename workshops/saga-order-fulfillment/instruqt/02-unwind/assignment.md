---
slug: unwind
id: 93vlkypipln6
type: challenge
title: 2. Unwind
teaser: The undos are registered with a Saga. Nothing runs them. Make them run, newest
  first, past a failing one.
notes:
- type: text
  contents: |-
    # The undos exist. Why did nothing run them?

    Every step already registers its undo with a `Saga`. When a step fails, the
    Workflow catches the failure and calls `unwind`.

    `unwind` is empty. One call fixes that. One option decides whether a refund
    that fails leaves the customer's stock locked forever.
- type: text
  contents: |-
    # The Saga class in three calls

    ```java
    Saga saga = new Saga(options);              // an empty stack of undos
    saga.addCompensation(activities::undoX, x); // push one undo
    saga.compensate();                          // run them
    ```

    `Saga.Options` has two switches. **Sequential** is the default: one undo at
    a time, newest first. **`continueWithError`** is off by default: the first
    undo that fails stops the rest.
tabs:
- id: elwdqyhxa1rx
  title: Exercise
  type: service
  hostname: workshop
  path: /?folder=/root/workshop/saga-order-fulfillment
  port: 8080
- id: lrjuntvykwlg
  title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/saga-order-fulfillment/exercise
- id: n8vfs7hzscth
  title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/saga-order-fulfillment/exercise
- id: kydyzi0ziun0
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

Make the Saga run every registered undo, newest first, and keep going when one
of them fails.

**Why it matters:** a failed order must give the customer their money back and
put the stock back on the shelf, in that order. A refund service that is down
must not also leave the stock locked.

# Run the undos (TODO 1)

**Observe.** Open `OrderFulfillmentWorkflowImpl.java` in the
[button label="Exercise" background="#444CE7"](tab-0) tab. Press `Ctrl+P`
(`Cmd+P` on a Mac), type the file name, then `Ctrl+G` to jump to a line.

<!-- snippet: exercise/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 1 -->
Lines 59-67 are the two helpers `fulfill` uses to build the Saga and to unwind
it:

```java
    // TODO 1 start: run the undos newest first, and keep going when one fails
    private static Saga newSaga() {
        return new Saga(new Saga.Options.Builder().build());
    }

    private static void unwind(Saga saga) {
        // Nothing runs the registered undos yet.
    }
    // TODO 1 end
```

**Learn.** `fulfill` calls `newSaga()` once at the start, registers an undo
after each step with `saga.addCompensation(...)`, and calls `unwind(saga)` in
its `catch (ActivityFailure e)` block, before it rethrows. But `unwind` is
empty, so when a step fails nothing runs the undos. That is the run you watched
in Challenge 1.

A Saga runs its undos newest first, one at a time, by default. By default it
also stops at the first undo that fails, which would leave the older steps
standing.

**Do.** Between the TODO 1 markers:

- [ ] In `unwind`, make the Saga run its compensations.
- [ ] In `newSaga`, set the one option that keeps the Saga going when an undo
  fails. Leave it sequential: that is what runs the undos newest first.

<details>
<summary>Hint</summary>

`Saga.Options` is built like every other Temporal options class:

```java
new SomeOptions.Builder().setSomething(true).build()
```

It has two setters, `setParallelCompensation` and `setContinueWithError`. Only
one of them is about failures. The Saga's own method that runs the undos is
named after the pattern's word for an undo.

</details>

<details>
<summary>Show solution</summary>

<!-- snippet: solution/src/main/java/io/temporal/devdays/saga/OrderFulfillmentWorkflowImpl.java TODO 1 -->
Lines 59-69 of `solution/.../OrderFulfillmentWorkflowImpl.java`:

```java
    // TODO 1 start: run the undos newest first, and keep going when one fails
    private static Saga newSaga() {
        // Sequential is the default: one undo at a time, newest first.
        // continueWithError: a failing undo does not stop the ones after it.
        return new Saga(new Saga.Options.Builder().setContinueWithError(true).build());
    }

    private static void unwind(Saga saga) {
        saga.compensate();
    }
    // TODO 1 end
```

</details>

Leave the other TODOs alone; they are the next Challenges.

# Run it

The Worker restarts when you save: its tab prints
`Fulfillment Worker polling fulfillment-tasks` again. If the
[button label="Worker" background="#444CE7"](tab-1) tab is not running it,
start it again there:

```bash,run
run-worker
```

In the [button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter --fail-dispatch
```

The [button label="Worker" background="#444CE7"](tab-1) tab now shows the undos:

```bash,nocopy
[ledger] reserve:ORD-XXXXXX
[ledger] charge:ORD-XXXXXX
17:16:51 WARN OrderFulfillmentWorkflowImpl - fulfillment failed for ORD-XXXXXX, unwinding: CourierUnavailable: no courier available for this route
[ledger] refund:ORD-XXXXXX
[ledger] release:ORD-XXXXXX
```

Read the last two lines. The refund happens **before** the stock is released:
the Saga runs the newest undo first. Undoing in the order you did things would
release stock while a charge still stood against it.

Open the newest Workflow in the
[button label="Temporal UI" background="#444CE7"](tab-3) tab (click refresh if
it isn't listed). Two Activities now appear **after** the failure:

| Event | Activity |
|---|---|
| **Activity Task Failed** | `DispatchCourier` |
| **Activity Task Completed** | `RefundPaymentIfCharged` |
| **Activity Task Completed** | `ReleaseInventoryIfReserved` |
| **Workflow Execution Failed** | |

The Workflow still ends **Failed**. Compensating is not succeeding: the caller
has to learn the order did not go through.

One thing `continueWithError` does not do is tell you. A failing undo is
skipped silently, and `compensate()` returns normally. Its **Activity Task
Failed** event in the Event History is the record a human has to act on.

# Checkpoint

Click **Check**. It passes when a failed courier leads to refund, then release,
and when one failing undo does not stop the next. If it fails, the message says
which of those is still missing.

---

**Please share your feedback so we can make better content for you.** The
**Feedback** tab takes a few seconds, and it is the only way we find out which
parts of this landed.
