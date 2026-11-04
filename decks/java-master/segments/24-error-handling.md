---
layout: section
---

<ZiggyHero scene="shield">

# Error handling

What fails, how it surfaces, and who retries it.

</ZiggyHero>

<!--
Source: PPT slides 156-167.

The original showed each code example twice, once per SDK, side by side. This
Workshop is Java only, so every pair became one Java snippet.
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
default policy doing its job. Intermittent is a longer `setInitialInterval` and
a higher `setBackoffCoefficient` on the `RetryOptions` in your
`ActivityOptions`. Permanent is `setNonRetryable(true)` on an
`ApplicationFailure` (or `ApplicationFailure.newNonRetryableFailure(...)`), or
the caller listing its type in `RetryOptions.setDoNotRetry(...)`: retrying it
is only burning money.
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
- Any other exception fails the **Workflow Task**, which Temporal retries
- `ApplicationFailure` is the failure to throw on purpose

</v-clicks>

<!--
Source: PPT slide 158.

The original's second half was about another SDK's error return values and
panics. The Java mapping:

- `throw ApplicationFailure.newFailure(...)` from a Workflow ends the Workflow
  Execution as Failed. That is a deliberate business decision. The same goes
  for any other `TemporalFailure`, such as a rethrown `ActivityFailure`.
- Any other exception, a `NullPointerException` or an
  `IllegalArgumentException`, is treated as a bug. The Workflow Task fails and
  is retried, and the Workflow stays open so you can fix the code and redeploy.
  The UI shows it as a failing Workflow Task, not a failed Workflow.
- To make a specific exception class fail the Workflow instead, register the
  implementation with
  `WorkflowImplementationOptions.newBuilder().setFailWorkflowExceptionTypes(InvalidOrderException.class)`
  (subclasses match too) as the first argument to
  `worker.registerWorkflowImplementationTypes(...)`.

All three are proven in `ErrorHandlingTest`.
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
called method throws, you expect to catch that exact exception in the caller.
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
- In Java they all extend `TemporalFailure`, in `io.temporal.failure`
- An exception thrown by an Activity reaches the Workflow as an **`ActivityFailure`**; a failed Child Workflow as a **`ChildWorkflowFailure`**
- Its `getCause()` holds what actually happened: `ApplicationFailure`, `TimeoutFailure`, `CanceledFailure`
- You can catch and handle it in your Workflow, if you want

</v-clicks>

<!--
Source: PPT slide 160.

The original's language note compared class hierarchies across SDKs. In Java
there is one hierarchy, rooted at `TemporalFailure` in `io.temporal.failure`:
`ApplicationFailure`, `ActivityFailure`, `ChildWorkflowFailure`,
`CanceledFailure`, `TimeoutFailure`, `TerminatedFailure`, `ServerFailure` and
`NexusOperationFailure`. They are unchecked exceptions: you catch them with
`catch` and inspect them with `instanceof` (pattern matching keeps it short).
Note the spelling: `CanceledFailure`, one L.

On the client, a Workflow that failed makes the stub call (or
`WorkflowStub.getResult`) throw `WorkflowFailedException`, from
`io.temporal.client`. That one is not a `TemporalFailure`: it is the client's
wrapper, and its `getCause()` is the Workflow's failure, such as the
`ApplicationFailure` the Workflow threw.

Cancellation can arrive wrapped, as an `ActivityFailure` or
`ChildWorkflowFailure` whose cause is a `CanceledFailure`, so check the cause
as well as the exception itself.

Don't extend these classes; you can't. `TemporalFailure`'s constructor is
package-private and `ApplicationFailure` is `final`. The only one your code
should ever throw is `ApplicationFailure`.
-->

---
layout: code-walkthrough
heading: Raising errors from an Activity
notes:
  - A message for humans. It shows up in the UI and in the Workflow's <code class="ref">ActivityFailure.getCause()</code>
  - A <strong>type</strong> for code. Retry Policies and Workflows match on it
  - Tell Temporal not to retry. This is a business rule, not a blip
---

::code::

```java {all|5|6|7}
@Override
public String chargePayment(Order order) {
  if (!cardHasFunds(order)) {
    throw ApplicationFailure.newBuilder()
        .setMessage("payment failed: insufficient funds")
        .setType("PaymentError")
        .setNonRetryable(true)
        .build();
  }
  return gateway.charge(order);
}
```

<!--
Source: PPT slide 161.

This is a method of `PaymentActivitiesImpl`, which implements the
`@ActivityInterface` `PaymentActivities`. `ApplicationFailure` comes from
`io.temporal.failure`; the import is off the slide.

- **Build 1 -** the message.
- **Build 2 -** the type. Any other exception an Activity throws becomes an
  `ApplicationFailure` on the way out, with its fully-qualified class name as
  the type (`java.lang.IllegalStateException`, say) and retryable. Throwing
  `ApplicationFailure` yourself lets you choose the type, attach details with
  `setDetails(...)`, and mark it non-retryable.
- **Build 3 -** `setNonRetryable(true)`. The shorthand is
  `ApplicationFailure.newNonRetryableFailure(message, type)`. The alternative,
  when the caller should decide, is
  `RetryOptions.newBuilder().setDoNotRetry("PaymentError")` in the
  `ActivityOptions` the Workflow builds its stub with.

Everything else, a timeout or a connection reset from `gateway.charge`, is left
to throw normally, and the Retry Policy handles it. That is the right default.

`ErrorHandlingTest` proves the Activity runs exactly once, and that a plain
`IllegalStateException` arrives typed `java.lang.IllegalStateException`.

Code: snippets/src/main/java/io/temporal/devdays/essentials/errors/PaymentActivitiesImpl.java
-->

---
layout: code-walkthrough
heading: Handling an ActivityFailure in a Workflow
notes:
  - Every Activity failure arrives as an <code>ActivityFailure</code>
  - Inspect <code>getCause()</code> to see what actually happened
  - Timed out, canceled, or a custom application failure with its <code>getType()</code>
  - Nothing matched. Rethrow, and the Workflow fails with the full cause chain
---

::code::

```java {all|5|6|7-12|14}
@Override
public String pay(Order order) {
  try {
    return payments.chargePayment(order);
  } catch (ActivityFailure e) {
    Throwable cause = e.getCause();
    if (cause instanceof TimeoutFailure) {
      // the Activity timed out
    } else if (cause instanceof CanceledFailure) {
      // the Activity was canceled
    } else if (cause instanceof ApplicationFailure af && af.getType().equals("PaymentError")) {
      return requestNewCard(order);
    }
    throw e;
  }
}
```

<!--
Source: PPT slide 162.

`payments` is the Activity stub, a field built with
`Workflow.newActivityStub(PaymentActivities.class, ...)` and a 30-second
`setStartToCloseTimeout`. `ActivityFailure`, `TimeoutFailure`,
`CanceledFailure` and `ApplicationFailure` all come from `io.temporal.failure`.
The field and imports are off the slide to save room; say them.

The stub call is a plain blocking call, so this is a plain `try`/`catch`.
`instanceof` with a pattern variable (`af`) is both the check and the cast.

The branch that does real work is the `PaymentError` one: a business outcome,
so the Workflow takes a different path, asking for a new card, rather than
failing. That is what "errors are part of the business logic" means.

`getType()` returns the string the Activity chose on the last slide. It is how
a Workflow tells failures apart without sharing exception classes with the
Activity, which may live in another service, or another language.

A Child Workflow works the same way: the child stub call throws a
`ChildWorkflowFailure`, and its `getCause()` is the child's own failure.
`ErrorHandlingTest` proves both, and that the rethrown `ActivityFailure` reaches
the client with its `ApplicationFailure` cause intact.

Code: snippets/src/main/java/io/temporal/devdays/essentials/errors/PaymentWorkflowImpl.java
-->

---
layout: code-stack
heading: Failing a Workflow with ApplicationFailure
proseMax: 32
---

::code::

```java
@Override
public OrderResult process(Order order) {
  // Only ApplicationFailure should be thrown on purpose from Workflow code
  throw ApplicationFailure.newFailure("unrecoverable state", "CriticalError");
}
```

::default::

A Temporal failure from Workflow code (`ApplicationFailure`, or a rethrown
`ActivityFailure`) **fails the Workflow Execution**. Any other exception fails
only the **Workflow Task**, and Temporal retries it until the code is fixed.

<!--
Source: PPT slide 163.

Say the consequence of getting this backwards. Throw a plain
`new IllegalArgumentException("invalid order")` meaning "this order is invalid"
and the Workflow does not fail. It sits there, retrying its Workflow Task,
because Temporal assumes it is a bug you will deploy a fix for. If you mean
"this Workflow has failed," say so with `ApplicationFailure`.

On the client, the stub call throws a `WorkflowFailedException`, and its
`getCause()` is this `ApplicationFailure`, with `getType()` `"CriticalError"`.

Code: snippets/src/main/java/io/temporal/devdays/essentials/errors/CriticalWorkflowImpl.java
-->

---
layout: default
---

# Raising and handling errors

- An exception thrown from an **Activity** fails that attempt
  - It is retried according to the policy, unless it is **non-retryable**
  - Only the final failure is an Event: `ActivityTaskFailed`, with the error details
- An `ApplicationFailure` (or rethrown `ActivityFailure`) from a **Workflow** fails the Workflow Execution
- Any other exception from a Workflow fails the **Workflow Task**, and is retried
- Throw **non-retryable** failures when you don't want an Activity retried

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

In Java, cancellation reaches the Workflow as a `CanceledFailure` (or an
`ActivityFailure` wrapping one) thrown from whatever call it is blocked in:
`Workflow.sleep`, an Activity stub call, `Promise.get()`. Catch it, run cleanup
Activities inside `Workflow.newDetachedCancellationScope(() -> ...).run()` so
they are not canceled too, and rethrow. `Workflow.newCancellationScope(...)`
is the other half: it lets the Workflow cancel a part of itself.

Reset is the one people do not know about and love once they do: after
deploying a fix for a bug that failed a batch of Workflows, reset them to just
before the failing Event and they re-run on the new code.

`temporal workflow reset --workflow-id <id> --type LastWorkflowTask` is the
common form.
-->
