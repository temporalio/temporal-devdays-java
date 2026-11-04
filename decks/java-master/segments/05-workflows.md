---
layout: section
---

<ZiggyHero scene="conduct">

# Java Workflows

The orchestration. Plain Java that Temporal can replay.

</ZiggyHero>

<!--
Source: PPT slides 38-50.

Two slides, stepped through ten times between them. Resist reading all the
annotations at once: the point of the build is that the room looks at one line
at a time.

The original split this into an interface slide and an implementation slide,
and so does this: in Java a Workflow is an interface (the contract a Client
calls) and a class that implements it (the code a Worker runs).
-->

---
layout: code-walkthrough
heading: Java Workflow interface
notes:
  - "<code>@WorkflowInterface</code> marks the contract. Its simple name, <code>TransferWorkflow</code>, is the Workflow <strong>Type</strong>"
  - "<code>@WorkflowMethod</code> is the entry point. Exactly one per Workflow; it runs for the whole Execution"
  - All arguments and return values must be <strong>serializable</strong>
---

::code::

```java {all|2-3|4-5|5}
// TransferWorkflow.java
@WorkflowInterface
public interface TransferWorkflow {
  @WorkflowMethod
  Receipt transfer(Transfer transfer);
}
```

<!--
Source: PPT slides 39-50, one build per slide in the original. The original
was a one-line order Workflow; this is a money transfer, so the same anatomy
carries a business decision and a compensation.
Code: snippets/src/main/java/io/temporal/devdays/essentials/workflows/TransferWorkflow.java,
run by snippets/src/test/java/io/temporal/devdays/essentials/workflows/TransferWorkflowTest.java.

- **Build 1 - the annotation and the name.** The SDK is split by concern:
  `io.temporal.workflow` for Workflow code, `io.temporal.activity`,
  `io.temporal.client`, `io.temporal.worker`. A Workflow imports from the first.
  The Workflow Type is the interface's simple name, `TransferWorkflow`, and a
  Client in another codebase can start it by that string (the runtime test does
  exactly that with an untyped stub). Rename the interface and you rename the
  Type, which matters once Workflows of the old name are running;
  `@WorkflowMethod(name = "...")` pins it.
- **Build 2 - the Workflow method.** One `@WorkflowMethod` per interface. When
  it returns, the Workflow Execution completes. The same interface can declare
  `@SignalMethod`, `@QueryMethod` and `@UpdateMethod` handlers; those get their
  own segments.
- **Build 3 - the signature.** Arguments and return values cross a process
  boundary as JSON, through Jackson. Records, POJOs, primitives, collections
  and `java.time` types work out of the box; an open connection or a lambda
  does not. A single object argument is the convention: you can add a field to
  the record later without breaking running Workflows.

The interface is also what the Client compiles against. It never sees the
implementation on the next slide.
-->

---
layout: code-walkthrough
heading: Java Workflow implementation
notes:
  - "A plain class that <code>implements</code> the interface. The Worker registers it with <code class=\"ref\">registerWorkflowImplementationTypes</code>"
  - "Activity stubs, typed by the <code>BankActivities</code> interface alone: the Activity implementation never enters Workflow code"
  - "Options per stub, set by the caller: 5 seconds for a fraud check, 30 seconds and five attempts to move money"
  - Calling a stub <strong>schedules</strong> the Activity. Branching on its result is a business decision, so it lives here
  - The money leaves the source account. Once it completes, it is in Event History for good
  - "If the deposit fails for good, put the money back. A compensation, in a plain <code>try</code>/<code>catch</code>"
  - The receipt is the Workflow's result, recorded in history too
---

::code::

```java {all|2|3,7|5,9-10|15|19|20-25|26}
// TransferWorkflowImpl.java
public class TransferWorkflowImpl implements TransferWorkflow {
  private final BankActivities fraud = Workflow.newActivityStub(BankActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(5))
          .build());
  private final BankActivities bank = Workflow.newActivityStub(BankActivities.class,
      ActivityOptions.newBuilder()
          .setStartToCloseTimeout(Duration.ofSeconds(30))
          .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(5).build())
          .build());

  @Override
  public Receipt transfer(Transfer transfer) {
    if (fraud.checkFraud(transfer)) {
      return Receipt.rejected("flagged by the fraud check");
    }

    bank.withdraw(transfer);
    try {
      bank.deposit(transfer);
    } catch (ActivityFailure e) {
      bank.refund(transfer);
      return Receipt.refunded(e.getMessage());
    }
    return Receipt.completed();
  }
}
```

<!--
Source: PPT slides 39-50, continued.
Code: snippets/src/main/java/io/temporal/devdays/essentials/workflows/TransferWorkflowImpl.java,
run by snippets/src/test/java/io/temporal/devdays/essentials/workflows/TransferWorkflowTest.java.

- **Build 1 - the class.** No base class, no framework. The Worker is given
  the class with `registerWorkflowImplementationTypes(TransferWorkflowImpl.class)`
  and creates a fresh instance per Workflow Execution, so fields are per-run
  state. What makes it safe to replay is what the code does not do: no
  `Thread.sleep`, no `System.currentTimeMillis()`, no `new Random()`, no
  threads of its own, no I/O. The `Workflow` class has a replay-safe
  replacement for each.
- **Build 2 - the stubs.** `Workflow.newActivityStub` returns a proxy that
  implements `BankActivities`. The Workflow only knows the interface; the
  Activity implementation, with its database driver and HTTP client, lives
  with the Worker and never runs here. Two stubs of the same interface is
  normal: each carries its own options.
- **Build 3 - the options.** `setStartToCloseTimeout` is how long one attempt
  may run. At least one of it or `setScheduleToCloseTimeout` is required. A
  fraud check that takes more than 5 seconds is broken; a bank call gets
  longer, and five attempts before the Workflow gives up on it. Note where the
  options live: on the caller. The Workflow decides how long it will wait; the
  Activity has no opinion.
- **Build 4 - the call, and the decision.** It looks like a method call and it
  is not one. It writes a Command to Temporal, which schedules the Activity on
  a Worker that may be a different process or machine, and the Workflow thread
  blocks until the result is in history. What to do with a flagged transfer is
  policy, so it is an `if` here, visible in the code and in the history, not
  buried in the Activity.
- **Build 5 - the withdrawal.** The result is recorded in Event History. If the
  Worker crashes after this line, the replay reads it back rather than
  withdrawing twice.
- **Build 6 - the compensation.** After five failed attempts, or one
  non-retryable failure such as a closed account
  (`ApplicationFailure.newNonRetryableFailure`), the stub throws an
  `ActivityFailure` and the Workflow refunds the source account. That is the
  Saga pattern in its smallest form; the Compensations section comes back to
  it.
- **Build 7 - the result.** The receipt goes back to whoever is waiting on the
  Workflow: the Client, on the Client slide.

If you only have time for three builds, take 2, 4 and 6.
-->
