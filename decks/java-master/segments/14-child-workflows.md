---
layout: section
---

<ZiggyHero scene="children">

# Child Workflows

Decomposing orchestration without losing the guarantees.

</ZiggyHero>

<!--
Source: PPT slides 99-107.
-->

---
layout: default
---

# When one Workflow isn't enough

- Some processes are too complex, too long, or too parallel for a single Workflow
- Splitting logic into functions or Activities only goes so far
- What if the sub-process itself needs durability, retries, and its own history?
- **Enter Child Workflows.**

<!--
Source: PPT slide 100.

Frame this as a natural scaling question. Developers hit it when a Workflow
starts doing too much: the history gets long, the logic gets tangled, or they
need the same sub-process reused across several parents. Child Workflows answer
"how do I decompose orchestration without losing Temporal's guarantees?"
-->

---
layout: default
---

# What is a Child Workflow?

- A Workflow launched by another Workflow
- Has its own execution, its own history, its own Retry Policy
- A first-class Temporal citizen. Not a function call, not an Activity
- Can be monitored, cancelled, and queried independently

<!--
Source: PPT slide 101.

The distinction to hammer home: a Child Workflow is not syntactic sugar. It is a
full Workflow Execution that happens to be started by a parent. That
independence is what makes it powerful, and what separates it from calling a
function.

In Java there is no child marker at all. A Child Workflow is an ordinary
`@WorkflowInterface` and its implementation, registered on a Worker like any
other; whether it is a child is decided entirely by who starts it. The same
Workflow can be a child here and started from a Client somewhere else.
-->

---
layout: default
---

# Child Workflow vs Activity: the decision

- **Use an Activity when** you're interacting with external systems, doing
  I/O, or calling an API
- **Use a Child Workflow when** you're orchestrating a multi-step sub-process,
  need independent retry logic, or are reusing complex logic across Workflows

<div v-click class="mt-8 text-2xl font-light">

Rule of thumb: if it needs its own history, it's a Child Workflow.

</div>

<!--
Source: PPT slide 102.

The slide developers most need. The instinct is to reach for Activities for
everything. The test: does this thing need to survive independently? Does it
have enough internal complexity to deserve its own audit trail? If yes, it is a
Child Workflow.

Other signals that point to a child: the work needs its own Task Queue and its
own Workers, or you want to see it as its own execution in the UI. And the
default worth stating plainly: if it is one unit of work, use an Activity. A
Child Workflow is heavier, a full execution with its own history.
-->

---
layout: default
---

# Parent-child lifecycle

What happens to the child when the parent closes is its Parent Close Policy.

<div class="grid grid-cols-3 gap-4 mt-6">
  <ParentClosePolicy v-click policy="TERMINATE" />
  <ParentClosePolicy v-click policy="ABANDON" />
  <ParentClosePolicy v-click policy="REQUEST_CANCEL" />
</div>

<!--
Source: PPT slide 103.

The parent starts the child, and the child gets its own Workflow ID. Each click
reveals one policy: the same parent, the same child, the same moment the parent
closes, and only the child's lane changes.

- **Click 1 - `TERMINATE`, the default.** The child is cut off when the parent
  closes. No cleanup code runs.
- **Click 2 - `ABANDON`.** The child keeps running on its own. ABANDON is the
  one people misunderstand: the child keeps running even though the parent is
  gone. That is intentional, and powerful for fire-and-forget sub-processes.
- **Click 3 - `REQUEST_CANCEL`.** The child is sent a cancellation request and
  can clean up gracefully, the same cancellation the Saga section uses to run
  compensations, before it ends as cancelled.

The policy applies whenever the parent closes, whether it completed, failed,
timed out or was terminated. Not only on failure. In code these are
`ParentClosePolicy.PARENT_CLOSE_POLICY_TERMINATE`, `PARENT_CLOSE_POLICY_ABANDON`
and `PARENT_CLOSE_POLICY_REQUEST_CANCEL`, from `io.temporal.api.enums.v1`, set
with `ChildWorkflowOptions.newBuilder().setParentClosePolicy(...)`.
-->

---
layout: code-walkthrough
heading: Spawning a child, waiting for the result
notes:
  - A typed stub for the child's <code>ShipmentWorkflow</code> interface. The same interface a Client would use
  - The child's Workflow ID. Plan this scheme; IDs must be unique
  - What happens to the child if this parent closes first
  - Calling <code>ship</code> starts the child and blocks until it returns. Its argument is the child's input
---

::code::

```java {all|3-4|6|7|9}
@Override
public String placeOrder(Order order) {
  ShipmentWorkflow shipment = Workflow.newChildWorkflowStub(
      ShipmentWorkflow.class,
      ChildWorkflowOptions.newBuilder()
          .setWorkflowId("shipment-" + order.orderId())
          .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_ABANDON)
          .build());
  return shipment.ship(order.orderId());
}
```

<!--
Source: PPT slide 104.
Code: snippets/src/main/java/io/temporal/devdays/essentials/child/OrderWorkflowImpl.java,
run by ChildWorkflowsTest. `OrderWorkflow.placeOrder` and `ShipmentWorkflow.ship`
are the names the Child Workflows Workshop uses.

- **Build 1 - the stub.** `Workflow.newChildWorkflowStub` takes the child's
  interface, not a string. Its Workflow Type is the interface name,
  `ShipmentWorkflow`, and the compiler checks the arguments and the return
  value for free. The child's implementation must be registered on a Worker
  polling its Task Queue, like any other Workflow; by default the child runs on
  the parent's Task Queue.
- **Build 2 - the ID.** `"shipment-" + orderId` is deterministic and
  meaningful: support can find the child by order number. A random ID is
  possible (`Workflow.randomUUID()` is replay-safe), but you lose the ability to
  find it. Leave `setWorkflowId` off and the SDK generates one.
- **Build 3 - the policy.** ABANDON here, so a parent that fails after
  dispatching does not kill a shipment that is already moving. Leave it off and
  you get TERMINATE.
- **Build 4 - the call.** Calling a method on the stub starts the child and
  blocks the Workflow until the child returns. The argument is the child's
  input, serialized like every Workflow argument. Same convention as a Client's
  Workflow stub.

To start a child without waiting, call it through `Async.function(shipment::ship,
orderId)`: that returns a `Promise` straight away, which you can `get()` later.
`Workflow.getWorkflowExecution(shipment).get()` waits only until the child has
started, which is what an ABANDON child that should outlive its parent needs.
That is the fan-out shape on the next slide.
-->

---
layout: default
---

# Practical use cases

- **Fan-out / fan-in.** Spawn N children in parallel, collect the results
- **Per-entity Workflows.** One child per user, order, or customer
- **Reusable sub-processes.** The same Child Workflow called from several Parent Workflows
- **Isolating long-running steps.** Keep the parent's history lean

<!--
Source: PPT slide 105.

Fan-out/fan-in gets the most excitement. The shape in Java is one child stub
per item, each started with `Async.function(stub::ship, id)` into a
`List<Promise<String>>`, then `Promise.allOf(promises).get()` to wait for them
all. Fifty children
processing a batch each, then a parent that aggregates. A compelling demo if
you have one ready.
-->

---
layout: default
---

# Gotchas

- Deeply nested child chains are hard to debug. Keep hierarchies shallow
- Each child has its own history limit. Continue-As-New applies to children too
- Don't use children just to avoid writing clean Activity code
- Workflow IDs must be unique. Plan your ID scheme carefully

<!--
Source: PPT slide 106.

The last one bites in production. If you spawn one child per entity, the ID
scheme (`"shipment-" + orderId`) is part of your system design, not an
afterthought. A collision fails the start, and it happens at 3am.

Tease Continue-As-New here. It is next.
-->

---
layout: default
---

# What you got

- Composable orchestration without sacrificing durability
- Sub-processes with their own history, retries, and lifecycle
- Independent monitoring and cancellation per child
- The building block for fan-out, per-entity, and reusable Workflow patterns

<!--
Source: PPT slide 107.

Close on composability. Temporal gives you the primitives; Child Workflows are
how you build architecture out of them.
-->
