---
layout: section
---

<ZiggyHero scene="launch">

# Start vs. Execute

Two ways to launch a Workflow, and one of them gives you a handle.

</ZiggyHero>

<!--
Source: PPT slides 55-58.

The original slides were in C# (`ExecuteWorkflowAsync` / `StartWorkflowAsync`);
these are rewritten against `@temporalio/client`.

Short segment, and the distinction matters the moment anyone wants to send a
Signal.
-->

---
layout: code-walkthrough
heading: "execute(): fire and wait"
notes:
  - One call. It starts the Workflow <strong>and</strong> waits for the result
  - "<code>workflowId</code> is yours to choose: a business ID, unique while the Workflow runs"
  - The Task Queue the Worker polls. A typo here and the Workflow waits forever
  - Nothing here runs until the Workflow completes
---

::code::

```ts {all|1|2|3|7}
const result = await client.workflow.execute(orderWorkflow, {
  workflowId: `order-${order.orderId}`,
  taskQueue: "order-task-queue",
  args: [order],
});

console.log(`Result: ${result.status}`);
```

<!--
Source: PPT slide 56.
Code: snippets/src/start-execute.client.ts, run by
snippets/tests/runtime/start-execute.test.ts.

Use this when you want to submit a Workflow and wait for the final answer, and
nothing else.

- **Build 1 - `execute()`.** The Workflow is started, the call waits until it
  finishes, and the result comes back. It is `start()` plus `handle.result()`
  in one call. Say that, because it makes the next slide obvious.
- **Build 2 - the Workflow ID.** Required in TypeScript. Make it meaningful,
  such as the order ID: Temporal refuses a second start with the ID of a
  Workflow that is still running (`WorkflowExecutionAlreadyStartedError`), so a
  retried HTTP request cannot start the same order twice.
- **Build 3 - the Task Queue.** Must match the Worker's, character for
  character.
- **Build 4 - the wait.** The catch: you have no handle while it runs, so you
  cannot Signal it, Query it, or cancel it from here.

Worth pre-empting: the client waiting has nothing to do with the Workflow's
durability. Kill the client here and the Workflow carries on. You have only lost
your ability to read the result from *this* process.
-->

---
layout: code-walkthrough
heading: "start(): fire, and keep the handle"
notes:
  - Returns as soon as the Workflow is <strong>started</strong>, not when it finishes
  - The handle is how you reach a running Workflow. <code>query</code> reads its state
  - "<code>signal</code> pushes data in: the human in the loop"
  - Ask for the result whenever you want it, from any process
---

::code::

```ts {all|1-5|7|8|10}
const handle = await client.workflow.start(approvalWorkflow, {
  workflowId: `approval-${order.orderId}`,
  taskQueue: "order-task-queue",
  args: [order],
});

console.log(`Status: ${await handle.query(getStatus)}`);
await handle.signal(approve, "alice");

const result = await handle.result();
console.log(`Result: ${result.status}`);
```

<!--
Source: PPT slides 57-58, merged: the original repeated the same slide twice.
Code: snippets/src/start-execute.client.ts, run by
snippets/tests/runtime/start-execute.test.ts.

Use this when you need to interact with the Workflow while it runs: the
human-in-the-loop case.

- **Build 1 - `start()`.** Returns once Temporal has durably recorded that the
  Workflow exists. Milliseconds, regardless of how long the Workflow runs.
- **Build 2 - Query.** Reads the current state without changing it. The Queries
  segment shows the Workflow side.
- **Build 3 - Signal.** Pushes data into the running Workflow. The Signals
  segment shows the Workflow side.
- **Build 4 - `result()`.** Waits until completion. Call it at any point, or
  never.

The fact that usually lands hardest: you can rebuild the handle from the
Workflow ID alone, in a different process, next week:
`client.workflow.getHandle(workflowId)`. The handle is not a socket or a
session. It is a name.
-->
