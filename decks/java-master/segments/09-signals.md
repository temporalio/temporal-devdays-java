---
layout: section
---

<ZiggyHero scene="signal">

# Java Signals

Asynchronous write / fire-and-forget

</ZiggyHero>

<!--
Source: PPT slides 71-79.

The human-in-the-loop primitive. If the room is here for approvals, reviews, or
anything with a person in it, this is the segment they came for.

The original split the Signal into an interface slide and an implementation
slide. Java has the same two halves, a `@SignalMethod` on the
`@WorkflowInterface` and its implementation in the class, so they sit together
in one walkthrough.
-->

---
layout: code-walkthrough
heading: Java Signals
notes:
  - "<code>@SignalMethod</code> on the <strong>interface</strong>: the method name is what Temporal routes on"
  - "Signals return <strong>nothing</strong>: <code>void</code>. They deliver data in, they do not read it out"
  - Workflow state is plain fields on the implementation
  - "The handler is the class's implementation of <code>approveWire</code>"
  - Mutate state. The waiting Workflow method sees the change and resumes
  - The Workflow blocks here, durably, until the condition is true
---

::code::

```java {all|6-7|7|11-12|21|22-23|16}
@WorkflowInterface
public interface WireTransferWorkflow {
  @WorkflowMethod
  String transfer(WireTransfer wire);

  @SignalMethod
  void approveWire(String officer);
}

public class WireTransferWorkflowImpl implements WireTransferWorkflow {
  private boolean approved = false;
  private String approver = "";

  @Override
  public String transfer(WireTransfer wire) {
    Workflow.await(() -> approved);
    return wire.wireId() + " approved by " + approver;
  }

  @Override
  public void approveWire(String officer) {
    approved = true;
    approver = officer;
  }
}
```

<!--
Source: PPT slides 72-79.
Code: snippets/src/main/java/io/temporal/devdays/essentials/signals/WireTransferWorkflow.java
and WireTransferWorkflowImpl.java, run by SignalsTest.

The scenario: a wire transfer over the bank's review threshold cannot leave
until a compliance officer signs off. The Workflow holds the wire; the officer's
review tool sends `approveWire` with their name.

- **Build 1 - the declaration.** `@SignalMethod` on the Workflow interface, so
  the Client's stub and the Worker share it. The method name, `approveWire`, is
  what Temporal routes on; `@SignalMethod(name = "...")` overrides it. The
  arguments are serializable, and they are what gets recorded in Event History
  as a `WorkflowExecutionSignaled` Event; on replay, the handler runs again with
  the same value.
- **Build 2 - `void`.** Builds 1 and 2 point at the same line on purpose. The
  SDK refuses a `@SignalMethod` that returns anything. A Signal is
  fire-and-forget: the sender gets an acknowledgement that it was recorded, not
  a result. If you need a result, that is an Update, two segments from now.
- **Build 3 - state.** Plain fields. The Worker creates one instance per
  Workflow Execution and rebuilds its fields by replay, so they survive a Worker
  restart without you storing anything. Initialize them in the declaration (or
  a `@WorkflowInit` constructor), not in the Workflow method: a Signal can
  arrive in the same Workflow Task as the start, and the handler must not see
  half-built state.
- **Build 4 - the handler.** Just the implementation of the interface method.
  The SDK registers it when it creates the instance, so there is no "register
  it in time" problem.
- **Build 5 - mutate.** The handler just sets state. No `synchronized`, no
  `volatile`: the SDK runs a Workflow's threads one at a time, deterministically.
  Signal handlers may also block, calling Activities or `Workflow.sleep`; if
  they do, have the Workflow method wait on
  `Workflow.await(() -> Workflow.isEveryHandlerFinished())` before it returns,
  so it does not complete under a running handler. A flag plus `Workflow.await`
  is the simplest shape and the one to teach first.
- **Build 6 - `Workflow.await`.** The equivalent of the original's
  `Workflow.await { approved }`. The Workflow is suspended here: no CPU, no
  Worker slot, and it can be evicted from the Worker's cache and rebuilt by
  replay later. It can wait over a weekend for compliance to come back. If the
  Worker restarts, it replays to this line and waits again.

Worth saying: the Server accepts a Signal even if no Worker is running. It waits
in Event History until a Worker picks the Workflow up.

The demo that sells this, if you have a terminal:
`temporal workflow signal --workflow-id <id> --name approveWire --input '"alice"'`.
-->
