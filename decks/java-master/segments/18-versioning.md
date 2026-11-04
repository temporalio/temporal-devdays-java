---
layout: section
---

<ZiggyHero scene="versions">

# Workflow Versioning

Changing code that running Workflows are still replaying.

</ZiggyHero>

<!--
Source: PPT slides 68-70, `{{SLIDE:versioning-before}}` and
`{{SLIDE:versioning-after}}`. The replay test, deprecatePatch and Worker
Versioning slides are new to the TypeScript Deck.

Frame the problem before the code, or the solution looks like ceremony: a
Workflow started last Tuesday is still running, and it replays against
*today's* code. Change the order of Activity calls and the replay no longer
matches the Event History. That is a non-determinism error, and it wedges the
Workflow.
-->

---
layout: code-stack
heading: "Before: the original Workflow"
proseMax: 26
---

::code::

```ts
import { proxyActivities } from "@temporalio/workflow";

const { activityA, activityB } = proxyActivities<typeof activities>({
  startToCloseTimeout: "10 seconds",
});

export async function myWorkflow(): Promise<void> {
  await activityA();
  await activityB();
}
```

::default::

Simple and clean: A, then B. Now the requirement changes: new executions should
run C instead of B.

<!--
Source: PPT slide 69. The original used scheduleToCloseTimeout; this uses
startToCloseTimeout to match every other Activity call in the Deck. The
`import type * as activities` line is cut for room.

Set the trap deliberately. Ask what happens if you just edit the last line and
redeploy.

The answer: every Workflow that already ran B replays, reaches this line,
expects B in the Event History, finds a command for C, and fails with a
non-determinism error ("Activity type of scheduled event 'activityB' does not
match activity type of activity command 'activityC'"). It does not fail loudly
at deploy time. It fails later, on the next Workflow Task, for executions you
had forgotten about. The Workflow is not failed or terminated: its Workflow
Task keeps retrying, so a fix deployed later lets it carry on.

Only executions that are still open are at risk. A Workflow that completed
before the deploy never replays again.
-->

---
layout: code-walkthrough
heading: "After: the versioned Workflow"
notes:
  - "<code>patched</code> marks one decision point, named by a string you choose"
  - New executions take this branch
  - Executions that started before the change keep taking the old one
---

::code::

```ts {all|10|11|13}
import { patched, proxyActivities } from "@temporalio/workflow";

const { activityA, activityB, activityC } = proxyActivities<typeof activities>({
  startToCloseTimeout: "10 seconds",
});

export async function myWorkflow(): Promise<void> {
  await activityA();

  if (patched("use-activity-c")) {
    await activityC();
  } else {
    await activityB();
  }
}
```

<!--
Source: PPT slide 70. The original's import list also pulled in
`METADATA_ENCODING_ID`, which this Workflow never uses; removed.

Walk it carefully: this is the segment people most often half-understand.

- **Build 1 - `patched`.** `'use-activity-c'` is a human-readable label for this
  one decision point. It is not a version number and there is no global
  ordering. Pick a name that says what changed.
- **Build 2 - the new branch.** Every execution that starts after this deploy
  takes it.
- **Build 3 - the old branch.** Every execution already in flight when the
  deploy landed keeps taking it, because the SDK can see from its Event History
  that it never took the patch.

The mechanism, in one sentence: on a new execution `patched` records a marker
in the Event History and returns true; on replay it returns true only if that
marker is there. The branch taken is the branch that was always taken.

A TypeScript detail if anyone asks: `patched` is not memoized when it returns
false, so it is safe in a loop. If one change touches several places in the
Workflow, call it once and keep the boolean.
-->

---
layout: code-walkthrough
heading: Retiring the patch
notes:
  - "<code>deprecatePatch</code> keeps recording the marker, but no longer branches on it"
  - Only the new path is left. The <code class="ref">else</code> branch is gone
---

::code::

```ts {all|4|5}
export async function myWorkflow(): Promise<void> {
  await activityA();

  deprecatePatch("use-activity-c");
  await activityC();
}
```

<!--
Ported from the PPT slide 70 notes, which described these steps in prose.

The part people skip, and the reason this looks like ceremony until it does
not: `patched` is step one of three.

- **Build 1 - `deprecatePatch`.** Deploy this once no execution that started
  before the patch is still running. It replays an execution that has the
  marker, and it does not fail one that has none.
- **Build 2 - the old branch is gone.** Only C remains.

Step three: once every execution that recorded the marker has finished, delete
the `deprecatePatch` line too.

Why the middle step exists, and the tests behind this Deck show both: deleting
`patched` outright breaks replay of any running execution that recorded the
marker ("Non-deprecated patch marker encountered ... but there is no
corresponding change command"). And deploying `deprecatePatch` too early breaks
any execution still on the B branch, because it would now run C.

How to know when to move on: list open executions of the type in the Web UI or
with `temporal workflow list`, and wait for the old ones to finish. Leave the
`if` in forever and you have built a museum of every change you ever made.
-->

---
layout: code-walkthrough
heading: Catch it before you deploy
notes:
  - Fetch the Event History of a real execution
  - Replay it against the new code. No Server, no Activities
  - It throws if the new code would decide differently
---

::code::

```ts {all|1-2|6|4-6}
const handle = client.workflow.getHandle(workflowId);
const history = await handle.fetchHistory();

// Throws DeterminismViolationError if the new code
// would make different decisions from this history
await Worker.runReplayHistory({ workflowsPath }, history);
```

<!--
New to the TypeScript Deck.

The non-determinism error from three slides ago shows up in production, days
later. This moves it into CI.

- **Build 1 - fetch.** Any execution will do; the useful ones are long-running
  executions that are open right now. You can also download a history as JSON
  from the Web UI or `temporal workflow show --output json` and check it in.
- **Build 2 - replay.** `runReplayHistory` runs the Workflow code in
  `workflowsPath` against the recorded Events. Activities are not executed;
  their results come from the history.
- **Build 3 - the verdict.** It resolves if the code makes the same decisions,
  and throws `DeterminismViolationError` if it does not.

Run it in CI against a handful of real histories and the unsafe edit from the
"Before" slide fails the build instead of the Workflow. `runReplayHistories`
takes many histories at once.
-->

---
layout: code-walkthrough
heading: Worker Versioning
notes:
  - The Worker joins a Worker Deployment
  - "<code>deploymentName</code> stays the same. <code>buildId</code> changes with every build"
  - A <strong>pinned</strong> execution runs to completion on the version it started on
---

::code::

```ts {all|6-7|8|9}
const worker = await Worker.create({
  connection,
  taskQueue: "orders",
  workflowsPath,
  activities,
  workerDeploymentOptions: {
    useWorkerVersioning: true,
    version: { deploymentName: "orders", buildId: "2.0.0" },
    defaultVersioningBehavior: "PINNED",
  },
});
```

<!--
New to the TypeScript Deck. Checked against @temporalio/worker 1.24.

Patching changes the code so old and new executions can share one Worker.
Worker Versioning is the other approach: run old and new Workers side by side,
and let Temporal route each execution to the version it belongs to.

- **Build 1 - opt in.** `workerDeploymentOptions` on `Worker.create`.
- **Build 2 - the version.** `deploymentName` names the service and stays
  constant; `buildId` names this build (a git hash, a semver, a build number).
- **Build 3 - the behaviour.** Required whenever `useWorkerVersioning` is true.
  `"PINNED"` keeps an execution on the version it started on, so it never sees
  new code and never needs a patch. `"AUTO_UPGRADE"` moves it to the current
  version, which brings patching back.

A new version takes no new executions until you make it current:
`temporal worker deployment set-current-version --deployment-name orders
--build-id 2.0.0`. Existing pinned executions stay where they are, so the old
Workers keep running until those drain.

When to pick which: pinning suits short Workflows and frequent deploys. A
Workflow that runs for months would keep old Workers alive for months, so
long-running ones use patching, or Continue-As-New onto the new version.
-->
