---
slug: make-the-undo-safe
id: ffh1mbpsw271
type: challenge
title: 4. Make the Undo Safe to Repeat
teaser: Registering first means an undo can run with nothing to undo. Make it a no-op
  when it does.
notes:
- type: text
  contents: |-
    # What happens when an undo runs with nothing to undo?

    You register each undo before its step now, so the Saga runs it even when
    the step never landed. The inventory service was down, nothing was
    reserved, and the release runs anyway.

    Releasing stock that was never held makes a phantom unit appear on the shelf.
    The `Saga` class cannot know what an undo did. Only the undo can.
tabs:
- id: nntcx1f1ykpx
  title: Exercise
  type: service
  hostname: workshop
  path: /?folder=/root/workshop/saga-order-fulfillment
  port: 8080
- id: hojea9qbzpjs
  title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/saga-order-fulfillment/exercise
- id: 9dfcxxwxfybn
  title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/saga-order-fulfillment/exercise
- id: ehvfpdsfqnel
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

Make each undo do nothing when there is nothing to undo, so it is safe to run
when its step never landed and safe to run twice.

**Why it matters:** an undo that always acts puts back stock that was never taken
and refunds charges that never happened.

# See it first

If the [button label="Worker" background="#444CE7"](tab-1) tab is not running
the Worker, start it there:

```bash,run
run-worker
```

In the [button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter --fail-reserve
```

The [button label="Worker" background="#444CE7"](tab-1) tab:

```bash,nocopy
17:17:13 WARN OrderFulfillmentWorkflowImpl - fulfillment failed for ORD-XXXXXX, unwinding: InventoryUnavailable: inventory service unavailable
[ledger] release:ORD-XXXXXX
```

Nothing was reserved, and stock was released anyway.

There is a second reason for the fix. A compensation is an ordinary Activity
with an ordinary retry policy, so Temporal can run it more than once even when it
succeeds. Releasing twice is the same bug.

# Step 1 of 2: make the release safe (TODO 3a)

**Observe.** Open `OrderActivitiesImpl.java` in the
[button label="Exercise" background="#444CE7"](tab-0) tab (`Ctrl+P`, then
the file name).

<!-- snippet: exercise/src/main/java/io/temporal/devdays/saga/OrderActivitiesImpl.java TODO 3a before=1 -->
Lines 48-50 are the top of the inventory undo:

```java
    public void releaseInventoryIfReserved(Order order) {
        // TODO 3a start: return early when Ledger.isReserved(order.orderId()) is false
        // TODO 3a end
```

**Learn.** Right after the TODO, the undo records a release without asking
whether anything is reserved. Since the last Challenge, each undo is registered
before its step, so it can run for a step that never took effect, and a retry
can run it twice. `Ledger.isReserved(orderId)` is `true` only while the stock
is still held.

**Do.**

- [ ] Between the TODO 3a markers, if nothing is reserved, print a no-op line
  and `return` before the release is recorded.

<details>
<summary>Hint</summary>

A guard clause: check first, leave early, and the rest of the method is
untouched.

```java
public void cancelBooking(String bookingId) {
    if (!bookings.exists(bookingId)) {
        return;
    }
    bookings.cancel(bookingId);
}
```

</details>

<details>
<summary>Show solution</summary>

<!-- snippet: solution/src/main/java/io/temporal/devdays/saga/OrderActivitiesImpl.java TODO 3a before=1 -->
Lines 48-54 of `solution/.../OrderActivitiesImpl.java`:

```java
    public void releaseInventoryIfReserved(Order order) {
        // TODO 3a start: return early when Ledger.isReserved(order.orderId()) is false
        if (!Ledger.isReserved(order.orderId())) {
            System.out.println("[compensate] nothing reserved for " + order.orderId() + ", no-op");
            return;
        }
        // TODO 3a end
```

</details>

# Step 2 of 2: make the refund safe (TODO 3b)

**Observe.**

<!-- snippet: exercise/src/main/java/io/temporal/devdays/saga/OrderActivitiesImpl.java TODO 3b before=1 -->
Lines 56-58 are the top of the refund undo:

```java
    public void refundPaymentIfCharged(Order order) {
        // TODO 3b start: return early when Ledger.isCharged(order.orderId()) is false
        // TODO 3b end
```

**Learn.** The same gap: a refund for a charge that never landed.
`Ledger.isCharged(orderId)` is `true` only while the charge still stands.

**Do.**

- [ ] Between the TODO 3b markers, the same guard, with `Ledger.isCharged`.

<details>
<summary>Show solution</summary>

<!-- snippet: solution/src/main/java/io/temporal/devdays/saga/OrderActivitiesImpl.java TODO 3b before=1 -->
Lines 60-66:

```java
    public void refundPaymentIfCharged(Order order) {
        // TODO 3b start: return early when Ledger.isCharged(order.orderId()) is false
        if (!Ledger.isCharged(order.orderId())) {
            System.out.println("[compensate] nothing charged for " + order.orderId() + ", no-op");
            return;
        }
        // TODO 3b end
```

</details>

This is the last TODO.

# Run it

Save, and the Worker restarts. Run the same order again:

```bash,run
run-starter --fail-reserve
```

```bash,nocopy
17:16:54 WARN OrderFulfillmentWorkflowImpl - fulfillment failed for ORD-XXXXXX, unwinding: InventoryUnavailable: inventory service unavailable
[compensate] nothing reserved for ORD-XXXXXX, no-op
```

Now read the newest Workflow in the
[button label="Temporal UI" background="#444CE7"](tab-3) tab (click refresh if
it isn't listed). `ReleaseInventoryIfReserved` is **still there**, and it
still shows **Activity Task Completed**. The guard did not stop it running: an
undo registered before its step always runs. What changed is what it *did*.
Idempotency is about the effect, not about skipping the call.

# Checkpoint

Click **Check**. It passes when each undo does nothing with nothing to undo and
acts once when run twice. If it fails, the message names TODO 3a or 3b.

# What you know now

- A distributed transaction has no rollback. Each undo is code you write.
- The `Saga` class holds the undos and runs them newest first, or you undo
  into an inconsistent state.
- Its default stops at the first failing undo. `setContinueWithError(true)`
  keeps one failing undo from stranding the rest.
- Register the undo **before** the step, because a step can land its effect and
  still report failure.
- So every undo must cope with nothing to undo, and with running twice.

---

**Please share your feedback so we can make better content for you.** The
**Feedback** tab takes a few seconds, and it is the only way we find out which
parts of this landed.
