---
layout: section
---

<ZiggyHero scene="shield">

# Error handling

What fails, how it surfaces, and who retries it.

</ZiggyHero>

<!--
Source: PPT slides 156-167. Ported from the Python Deck.

The original showed each code example twice, once per SDK, side by side. This
Workshop is TypeScript only, so every pair became one TypeScript snippet.
-->

---
layout: default
---

# Error handling concepts (1)

<v-clicks>

- You can categorize failures as **platform** or **application**
  - **Platform:** caused by things beyond your application code's control
  - **Application:** caused by problems with application code or input data
  - Tell them apart by asking: does detecting and fixing it need knowledge of the application?
- You can also classify them by how likely they are to happen again
  - **Transient:** unlikely to recur. Retry with a short delay
  - **Intermittent:** likely to recur. Retry with a longer, increasing delay
  - **Permanent:** guaranteed to recur. Needs manual intervention

</v-clicks>

<!--
Source: PPT slide 157. Eight builds in the original; `v-clicks` gives one per
bullet.

The second classification maps directly onto a Retry Policy. Transient is the
default policy doing its job. Intermittent is a longer `initialInterval` and a
higher `backoffCoefficient` in the `retry` option. Permanent is
`nonRetryable: true` on an `ApplicationFailure`: retrying it is only burning
money.
-->

---
layout: default
---

# Error handling concepts (2)

<v-clicks>

- **Idempotency** is a general concern for distributed systems
  - Will multiple invocations of your operation change application state for the worse?
  - It matters for Activities, because they may run more than once
  - Temporal strongly recommends making your Activities idempotent
- A Temporal failure thrown from Workflow code (`ApplicationFailure`, or a rethrown `ActivityFailure`) **fails the Workflow Execution**
- Any other error fails the **Workflow Task**, which Temporal retries
- `ApplicationFailure` is the failure to throw on purpose

</v-clicks>

<!--
Source: PPT slide 158.

The original's second half was about another SDK's error return values and
panics. The TypeScript mapping:

- `throw ApplicationFailure.create(...)` from a Workflow ends the Workflow
  Execution as Failed. That is a deliberate business decision.
- Any other error, a `TypeError` or a plain `new Error(...)`, is treated as a
  bug. The Workflow Task fails and is retried, and the Workflow stays open so
  you can fix the code and redeploy. The UI shows it as a failing Workflow Task,
  not a failed Workflow.
- To make a specific error class fail the Workflow instead, list it in
  `setWorkflowOptions({ failureExceptionTypes: [InvalidOrderError] }, myWorkflow)`
  (matched with `instanceof`), or by name in the Worker option
  `workflowFailureErrorTypes: { "*": ["InvalidOrderError"] }`. Both are marked
  experimental in SDK 1.24.
-->

---
layout: default
---

# The Temporal error model

<v-clicks>

- Temporal is polyglot: a Workflow and its Activities can be written in different languages
- If an Activity throws an error, it must reach the Workflow
- That has to work whichever languages the Workflow and Activity use
- So, as with data, errors cross language boundaries
- Errors are serialized in a language-neutral format (protobuf)

</v-clicks>

<!--
Source: PPT slide 159.

Temporal's error model can seem counterintuitive at first. Normally, when a
called function throws, you expect to catch that exact error in the caller.
Because of Temporal's design, you catch a wrapper instead.

Each SDK uses its own native error handling; Temporal converts the errors into
its own failure types: Application Failures, Task failures, and so on. Under the
hood, Temporal talks gRPC, which uses protobufs, and it has a set of protobuf
messages for failures that preserve context (message, type, stack trace, cause
chain) across process and language boundaries.
-->

---
layout: default
---

# How Temporal represents failures

<v-clicks>

- Every failure in Temporal is represented as a **Temporal failure**
- In TypeScript they all extend `TemporalFailure`, from `@temporalio/common`
- An error thrown by an Activity reaches the Workflow as an **`ActivityFailure`**; a failed Child Workflow as a **`ChildWorkflowFailure`**
- Its `cause` holds what actually happened: `ApplicationFailure`, `TimeoutFailure`, `CancelledFailure`
- You can catch and handle it in your Workflow, if you want

</v-clicks>

<!--
Source: PPT slide 160.

The original's language note compared class hierarchies across SDKs. In
TypeScript there is one hierarchy, rooted at `TemporalFailure`, and you inspect
it with `instanceof`. Workflow code imports the classes from
`@temporalio/workflow`, which re-exports them.

On the client, a Workflow that failed makes `handle.result()` (or
`client.workflow.execute`) reject with a `WorkflowFailedError` from
`@temporalio/client`. That one is not a `TemporalFailure`: it is the client's
wrapper, and its `cause` is the Workflow's failure, such as the
`ApplicationFailure` the Workflow threw.

Cancellation can arrive wrapped, as an `ActivityFailure` or
`ChildWorkflowFailure` whose cause is a `CancelledFailure`. To ask "is this a
cancellation?" whatever the wrapping, use `isCancellation(err)` from
`@temporalio/workflow`.

Don't extend these classes. The SDK's own comment says the only one your code
should ever throw is `ApplicationFailure`.
-->

---
layout: code-walkthrough
heading: Raising errors from an Activity
notes:
  - A message for humans. It shows up in the UI and in the Workflow's <code class="ref">ActivityFailure.cause</code>
  - A <strong>type</strong> for code. Retry Policies and Workflows match on it
  - Tell Temporal not to retry. This is a business rule, not a blip
---

::code::

```ts {all|6|7|8}
import { ApplicationFailure } from "@temporalio/common";

export async function chargePayment(order: Order): Promise<string> {
  if (!(await cardHasFunds(order))) {
    throw ApplicationFailure.create({
      message: "payment failed: insufficient funds",
      type: "PaymentError",
      nonRetryable: true,
    });
  }
  return gateway.charge(order);
}
```

<!--
Source: PPT slide 161.

- **Build 1 -** the message.
- **Build 2 -** the type. Any other error an Activity throws becomes an
  `ApplicationFailure` on the way out, with its class name as the type
  (`TypeError`, say) and retryable. Throwing `ApplicationFailure` yourself lets
  you choose the type, attach `details`, and mark it non-retryable.
- **Build 3 -** `nonRetryable: true`. The shorthand is
  `ApplicationFailure.nonRetryable(message, type)`. The alternative, when the
  caller should decide, is `retry: { nonRetryableErrorTypes: ["PaymentError"] }`
  in the Workflow's `proxyActivities` options.

Everything else, a timeout or a connection reset from `gateway.charge`, is left
to throw normally, and the Retry Policy handles it. That is the right default.

`@temporalio/activity` re-exports `ApplicationFailure` too; either import works.
-->

---
layout: code-walkthrough
heading: Handling an ActivityFailure in a Workflow
notes:
  - Every Activity failure arrives as an <code>ActivityFailure</code>
  - Inspect the <code>cause</code> to see what actually happened
  - Timed out, cancelled, or a custom application failure with its <code>type</code>
  - Nothing matched. Rethrow, and the Workflow fails with the full cause chain
---

::code::

```ts {all|5|6|7-13|15}
export async function paymentWorkflow(order: Order): Promise<string> {
  try {
    return await chargePayment(order);
  } catch (err) {
    if (err instanceof ActivityFailure) {
      const cause = err.cause;
      if (cause instanceof TimeoutFailure) {
        // the Activity timed out
      } else if (cause instanceof CancelledFailure) {
        // the Activity was cancelled
      } else if (cause instanceof ApplicationFailure) {
        if (cause.type === "PaymentError") return await requestNewCard(order);
      }
    }
    throw err;
  }
}
```

<!--
Source: PPT slide 162.

`chargePayment` comes from `proxyActivities` with
`{ startToCloseTimeout: "30 seconds" }`, and `ActivityFailure`,
`TimeoutFailure`, `CancelledFailure` and `ApplicationFailure` all come from
`@temporalio/workflow`. The imports are off the slide to save room; say them.

The `catch` gets `err` as `unknown`, so `instanceof` is both the check and the
type narrowing.

The branch that does real work is the `PaymentError` one: a business outcome,
so the Workflow takes a different path, asking for a new card, rather than
failing. That is what "errors are part of the business logic" means.

`cause.type` is the string the Activity chose on the last slide. It is how a
Workflow tells failures apart without sharing error classes with the Activity,
which may live in another service, or another language.

A Child Workflow works the same way: `executeChild` rejects with a
`ChildWorkflowFailure`, and its `cause` is the child's own failure.
-->

---
layout: code-stack
heading: Failing a Workflow with ApplicationFailure
proseMax: 32
---

::code::

```ts
export async function criticalWorkflow(order: Order): Promise<OrderResult> {
  // Only ApplicationFailure should be thrown on purpose from Workflow code
  throw ApplicationFailure.create({
    message: "unrecoverable state",
    type: "CriticalError",
  });
}
```

::default::

A Temporal failure from Workflow code (`ApplicationFailure`, or a rethrown
`ActivityFailure`) **fails the Workflow Execution**. Any other error fails only
the **Workflow Task**, and Temporal retries it until the code is fixed.

<!--
Source: PPT slide 163.

Say the consequence of getting this backwards. Throw a plain
`new Error("invalid order")` meaning "this order is invalid" and the Workflow
does not fail. It sits there, retrying its Workflow Task, because Temporal
assumes it is a bug you will deploy a fix for. If you mean "this Workflow has
failed," say so with `ApplicationFailure`.

On the client, the caller awaiting `handle.result()` gets a
`WorkflowFailedError`, and its `cause` is this `ApplicationFailure`, with
`type` `"CriticalError"`.
-->

---
layout: default
---

# Raising and handling errors

- An error thrown from an **Activity** fails that attempt
  - It is retried according to the policy, unless it is **non-retryable**
  - Only the final failure is an Event: `ActivityTaskFailed`, with the error details
- An `ApplicationFailure` (or rethrown `ActivityFailure`) from a **Workflow** fails the Workflow Execution
- Any other error from a Workflow fails the **Workflow Task**, and is retried
- Throw **non-retryable** errors when you don't want an Activity retried

<!--
Source: PPT slide 164.

The summary slide for the last four. If the room has a UI open, fail an
Activity and show the `ActivityTaskFailed` Event: the message, the type, the
stack trace and the retry state are there. The attempt count is on the
`ActivityTaskStarted` Event just before it. Failed attempts before the last one
leave no Event (Timeouts and Retry Policies segment).
-->

---
layout: default
---

# Recovering from failure

Temporal gives you a few options for recovering from persistent failure.

- **Cancel** a Workflow Execution: graceful, allows cleanup before closing
- **Terminate** a Workflow Execution: forceful, no cleanup
- **Reset** a Workflow Execution: continue from an earlier point in its Event History
- The application may also support **rolling back** to a previous state
  - Often with the **Saga pattern**: a series of related operations, each
    depending on the one before
  - On failure, compensating transactions revert the changes

<!--
Source: PPT slide 167.

In TypeScript, cancellation reaches the Workflow as a `CancelledFailure` (or a
failure wrapping one) thrown from whatever it is awaiting. Catch it, check it
with `isCancellation(err)`, run cleanup Activities inside
`CancellationScope.nonCancellable(...)` so they are not cancelled too, and
rethrow.

Reset is the one people do not know about and love once they do: after
deploying a fix for a bug that failed a batch of Workflows, reset them to just
before the failing Event and they re-run on the new code.

`temporal workflow reset --workflow-id <id> --type LastWorkflowTask` is the
common form.
-->
