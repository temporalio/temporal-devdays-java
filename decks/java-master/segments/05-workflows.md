---
layout: section
---

<ZiggyHero scene="conduct">

# TypeScript Workflows

The orchestration. Plain async code that Temporal can replay.

</ZiggyHero>

<!--
Source: PPT slides 38-50.

One slide, stepped through ten times. Resist reading all ten annotations at
once: the point of the build is that the room looks at one line at a time.

The original split this into an interface slide and an implementation slide.
TypeScript has no separate interface. One exported function is both the
definition and the implementation, so the two walkthroughs collapse into one.
-->

---
layout: code-walkthrough
heading: TypeScript Workflow anatomy
notes:
  - Workflow code imports from <code>@temporalio/workflow</code> and nothing else from the SDK
  - "<strong>Type only</strong>: the Activity code never enters the Workflow bundle"
  - "Activity stubs. <code>typeof activities</code> gives them every Activity's signature"
  - "Options per stub, set by the caller: 5 seconds for a fraud check, 30 seconds and five attempts to move money"
  - "<code>export</code> is the registration. The function name is the Workflow <strong>Type</strong>"
  - All arguments and return values must be <strong>serializable</strong>
  - Calling a stub <strong>schedules</strong> the Activity. Branching on its result is a business decision, so it lives here
  - The money leaves the source account. Once it completes, it is in Event History for good
  - If the deposit fails for good, put the money back. A compensation, in a plain <code>try</code>/<code>catch</code>
  - The receipt is the Workflow's result, recorded in history too
---

::code::

```ts {all|2|3-4|6,9|7,10-11|14|14|15-17|19|20-25|26}
// workflows.ts
import { proxyActivities } from "@temporalio/workflow";
import type * as activities from "./activities";
import type { Receipt, Transfer } from "./models";

const { checkFraud } = proxyActivities<typeof activities>({
  startToCloseTimeout: "5 seconds",
});
const { withdraw, deposit, refund } = proxyActivities<typeof activities>({
  startToCloseTimeout: "30 seconds",
  retry: { maximumAttempts: 5 },
});

export async function transferWorkflow(transfer: Transfer): Promise<Receipt> {
  if (await checkFraud(transfer)) {
    return { status: "rejected", reason: "flagged by the fraud check" };
  }

  await withdraw(transfer);
  try {
    await deposit(transfer);
  } catch (err) {
    await refund(transfer);
    return { status: "refunded", reason: String(err) };
  }
  return { status: "completed" };
}
```

<!--
Source: PPT slides 39-50, one build per slide in the original. The original
was a one-line order Workflow; this is a money transfer, so the same anatomy
carries a business decision and a compensation.
Code: snippets/src/workflows-anatomy.workflows.ts, run by
snippets/tests/runtime/workflows-anatomy.test.ts.

- **Build 1 - the package.** The SDK is split by concern: `@temporalio/workflow`,
  `/activity`, `/client`, `/worker`. A Workflow file imports only the first. The
  split is a guardrail, not a filing convention: the Worker bundles this file
  and runs it in a deterministic V8 isolate, where Node built-ins such as `fs`
  or `http` do not exist.
- **Build 2 - `import type`.** The `type` keyword erases the import at compile
  time; it exists only for the type checker. The Workflow gets the *shape* of
  the Activities without pulling a line of their code into the bundle. Forget
  the `type` and the bundler pulls the Activity module, and everything it
  imports, into the isolate; one import of a Node built-in or a database driver
  and the Worker fails to build its Workflow bundle. This is the single most
  TypeScript-specific idea in the segment.
- **Build 3 - the stubs.** `proxyActivities` returns an object with one function
  per Activity, typed from the module. Destructure the ones you call. Two calls
  to it is normal: each stub carries its own options.
- **Build 4 - the options.** `startToCloseTimeout` is how long one attempt may
  run. At least one of it or `scheduleToCloseTimeout` is required. A fraud
  check that takes more than 5 seconds is broken; a bank call gets longer, and
  five attempts before the Workflow gives up on it. Note where the options
  live: on the caller. The Workflow decides how long it will wait; the
  Activity has no opinion.
- **Build 5 - the export.** There is no decorator. Exporting an `async` function
  from the module the Worker loads is the whole registration. The Workflow Type
  is the function name, `transferWorkflow`, and a Client in another codebase
  can start it by that string. Rename the function and you rename the Type,
  which matters once Workflows of the old name are running.
- **Build 6 - the signature.** Arguments and return values cross a process
  boundary as JSON. Interfaces, primitives, arrays and plain objects work out of
  the box; a `Date` arrives as a string and a class instance loses its methods.
  A single object argument is the convention: you can add a field later without
  breaking running Workflows.
- **Build 7 - the call, and the decision.** It looks like a function call and
  it is not one. It writes a command to Temporal, which schedules the Activity
  on a Worker that may be a different process or machine. What to do with a
  flagged transfer is policy, so it is an `if` here, visible in the code and in
  the history, not buried in the Activity.
- **Build 8 - the withdrawal.** The result is recorded in Event History. If the
  Worker crashes after this line, the replay reads it back rather than
  withdrawing twice.
- **Build 9 - the compensation.** After five failed attempts, or one
  non-retryable failure such as a closed account, `deposit` throws an
  `ActivityFailure` and the Workflow refunds the source account. That is the
  Saga pattern in its smallest form; the Compensations section comes back to
  it.
- **Build 10 - the result.** The receipt goes back to whoever is waiting on the
  Workflow: the Client, on the Client slide.

If you only have time for three builds, take 2, 7 and 9.
-->
