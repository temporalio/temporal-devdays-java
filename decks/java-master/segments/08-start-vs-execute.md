---
layout: section
---

<ZiggyHero scene="launch">

# Start vs. Execute

Two ways to launch a Workflow, and one of them gives you a handle.

</ZiggyHero>

<!--
Source: PPT slides 55-58.

In Java, "execute" is calling the Workflow method on a typed stub, and "start" is
`WorkflowClient.start`.

Short segment, and the distinction matters the moment anyone wants to send a
Signal.
-->

---
layout: code-walkthrough
heading: "Call the Workflow method: fire and wait"
notes:
  - A typed stub. Building it starts <strong>nothing</strong>
  - "The Workflow ID is yours to choose: a business ID, unique while the Workflow runs"
  - The Task Queue the Worker polls. A typo here and the Workflow waits forever
  - One call. It starts the Workflow <strong>and</strong> blocks until the result comes back
  - Nothing here runs until the Workflow completes
---

::code::

```java {all|1-2|4|5|8|9}
OrderWorkflow workflow = client.newWorkflowStub(
    OrderWorkflow.class,
    WorkflowOptions.newBuilder()
        .setWorkflowId("order-" + order.orderId())
        .setTaskQueue("order-task-queue")
        .build());

OrderResult result = workflow.processOrder(order);
System.out.println("Result: " + result.status());
```

<!--
Source: PPT slide 56.
Code: snippets/src/main/java/io/temporal/devdays/essentials/startexecute/StartExecuteClient.java
(`executeOrder`), run by StartExecuteTest.

Use this when you want to submit a Workflow and wait for the final answer, and
nothing else.

- **Build 1 - the stub.** `newWorkflowStub` with options builds a proxy for a
  Workflow that does not exist yet. No network call happens here.
- **Build 2 - the Workflow ID.** Optional in Java: leave it out and the SDK
  generates a UUID. Set it anyway, and make it meaningful, such as the order ID:
  Temporal refuses a second start with the ID of a Workflow that is still
  running (`WorkflowExecutionAlreadyStarted`), so a retried HTTP request cannot
  start the same order twice.
- **Build 3 - the Task Queue.** Required, and must match the Worker's,
  character for character.
- **Build 4 - the call.** Calling the `@WorkflowMethod` starts the Workflow and
  blocks this thread until it finishes, then returns the result. It is
  `WorkflowClient.start` plus `getResult` in one call. Say that, because it
  makes the next slide obvious. If you would rather not block a thread,
  `WorkflowClient.execute(workflow::processOrder, order)` returns a
  `CompletableFuture` instead.
- **Build 5 - the wait.** The catch: this thread is parked inside the call, so
  you cannot Signal it, Query it, or cancel it from here.

Worth pre-empting: the client waiting has nothing to do with the Workflow's
durability. Kill the client here and the Workflow carries on. You have only lost
your ability to read the result from *this* process.
-->

---
layout: code-walkthrough
heading: "WorkflowClient.start(): fire, and keep the handle"
notes:
  - Returns as soon as the Workflow is <strong>started</strong>, not when it finishes
  - "The stub is now your handle. <code>getStatus</code> is a Query: it reads state"
  - "<code>approve</code> is a Signal: it pushes data in. The human in the loop"
  - Ask for the result whenever you want it, from any process
---

::code::

```java {all|7|9|10|12}
ApprovalWorkflow workflow = client.newWorkflowStub(
    ApprovalWorkflow.class,
    WorkflowOptions.newBuilder()
        .setWorkflowId("approval-" + order.orderId())
        .setTaskQueue("order-task-queue")
        .build());
WorkflowClient.start(workflow::run, order);

System.out.println("Status: " + workflow.getStatus());
workflow.approve("alice");

OrderResult result = WorkflowStub.fromTyped(workflow).getResult(OrderResult.class);
System.out.println("Result: " + result.status());
```

<!--
Source: PPT slides 57-58, merged: the original repeated the same slide twice.
Code: snippets/src/main/java/io/temporal/devdays/essentials/startexecute/StartExecuteClient.java
(`startApproval`), run by StartExecuteTest.

Use this when you need to interact with the Workflow while it runs: the
human-in-the-loop case.

- **Build 1 - `WorkflowClient.start`.** Takes a method reference to the
  Workflow method and its arguments. Returns once Temporal has durably recorded
  that the Workflow exists, with its `WorkflowExecution` (ID and Run ID).
  Milliseconds, regardless of how long the Workflow runs.
- **Build 2 - Query.** Calling a `@QueryMethod` on the stub reads the current
  state without changing it. The Queries segment shows the Workflow side.
- **Build 3 - Signal.** Calling a `@SignalMethod` pushes data into the running
  Workflow and returns once the Server has recorded it. The Signals segment
  shows the Workflow side.
- **Build 4 - `getResult`.** `WorkflowStub.fromTyped` gives the untyped view of
  the same Workflow; `getResult` blocks until completion. Call it at any point,
  or never. `getResultAsync` returns a `CompletableFuture`.

The fact that usually lands hardest: you can rebuild the handle from the
Workflow ID alone, in a different process, next week:
`client.newWorkflowStub(ApprovalWorkflow.class, workflowId)`. The stub is not a
socket or a session. It is a name.
-->
