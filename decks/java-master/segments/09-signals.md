---
layout: section
---

<ZiggyHero scene="signal">

# TypeScript Signals

Asynchronous write / fire-and-forget

</ZiggyHero>

<!--
Source: PPT slides 71-79.

The human-in-the-loop primitive. If the room is here for approvals, reviews, or
anything with a person in it, this is the segment they came for.

The original split the Signal into an interface slide and an implementation
slide. In TypeScript the Signal is a definition at module level plus a handler
inside the Workflow function, so it is one walkthrough.
-->

---
layout: code-walkthrough
heading: TypeScript Signals
notes:
  - "<code>defineSignal</code> at <strong>module level</strong>: the name Temporal routes on, and the argument types"
  - Signals return <strong>nothing</strong>. They deliver data in, they do not read it out
  - Workflow state is plain local variables inside the Workflow function
  - "<code>setHandler</code> connects the definition to a handler, inside the Workflow"
  - Mutate state. The waiting Workflow body sees the change and resumes
  - The Workflow blocks here, durably, until the condition is true
---

::code::

```ts {all|3|3|8-9|11|12-13|16}
import { condition, defineSignal, setHandler } from "@temporalio/workflow";

export const approveWire = defineSignal<[string]>("approveWire");

export async function wireTransferWorkflow(
  wire: WireTransfer,
): Promise<string> {
  let approved = false;
  let approver = "";

  setHandler(approveWire, (officer: string) => {
    approved = true;
    approver = officer;
  });

  await condition(() => approved);
  return `${wire.wireId} approved by ${approver}`;
}
```

<!--
Source: PPT slides 72-79.
Code: snippets/src/signals.workflows.ts, run by
snippets/tests/runtime/signals.test.ts.

The scenario: a wire transfer over the bank's review threshold cannot leave
until a compliance officer signs off. The Workflow holds the wire; the officer's
review tool sends `approveWire` with their name.

- **Build 1 - the definition.** A standalone constant, exported so the Client
  can import it too. The string `"approveWire"` is what Temporal routes on. The type
  parameter `<[string]>` is a tuple of the handler's arguments, which is why it
  is in brackets. The arguments are serializable, and they are what gets
  recorded in Event History as a `WorkflowExecutionSignaled` Event; on replay,
  the handler runs again with the same value.
- **Build 2 - no return type.** Builds 1 and 2 point at the same line on
  purpose: `defineSignal` takes argument types only. A Signal is
  fire-and-forget: the sender gets an acknowledgement that it was recorded, not
  a result. If you need a result, that is an Update, two segments from now.
- **Build 3 - state.** Plain `let` variables. Temporal rebuilds them by replay,
  so they survive a Worker restart without you storing anything.
- **Build 4 - `setHandler`.** Definition outside, handler inside. Register it at
  the top of the Workflow, before the first `await`. A Signal that arrives
  before its handler exists is buffered and delivered once you register it, so
  nothing is lost either way.
- **Build 5 - mutate.** The handler just sets state. Signal handlers may also be
  `async` and await Activities or Timers; if they do, have the Workflow wait on
  `condition(allHandlersFinished)` before it returns, so it does not complete
  under a running handler. A flag plus `condition` is the simplest shape and the
  one to teach first.
- **Build 6 - `condition`.** The equivalent of the original's
  `Workflow.await { approved }`. The Workflow is suspended here: no CPU, no
  Worker slot, and it can be evicted from the Worker's cache and rebuilt by
  replay later. It can wait over a weekend for compliance to come back. If the Worker restarts, it replays to this
  line and waits again.

Worth saying: the Server accepts a Signal even if no Worker is running. It waits
in Event History until a Worker picks the Workflow up.

The demo that sells this, if you have a terminal:
`temporal workflow signal --workflow-id <id> --name approveWire --input '"alice"'`.
-->
