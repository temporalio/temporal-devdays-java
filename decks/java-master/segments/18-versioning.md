---
layout: section
---

<ZiggyHero scene="versions">

# Workflow Versioning

Changing code that running Workflows are still replaying.

</ZiggyHero>

<!--
Source: PPT slides 68-70, `{{SLIDE:versioning-before}}` and
`{{SLIDE:versioning-after}}`.

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

```java
private final VersioningActivities activities =
    Workflow.newActivityStub(
        VersioningActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .build());

@Override
public void run() {
  activities.activityA();
  activities.activityB();
}
```

::default::

Simple and clean: A, then B. Now the requirement changes: new executions should
run C instead of B.

<!--
Source: PPT slide 69. The original used a Schedule-To-Close Timeout; this uses
Start-To-Close to match every other Activity call in the Deck. The class
declaration and imports are cut for room: these are the members of
`BeforeWorkflowImpl implements MyWorkflow`.

Code: snippets/src/main/java/io/temporal/devdays/essentials/versioning/BeforeWorkflowImpl.java

Set the trap deliberately. Ask what happens if you just edit the last line and
redeploy.

The answer: every Workflow that already ran B replays, reaches this line,
expects B in the Event History, finds a command for C, and fails with a
non-determinism error: `NonDeterministicException: [TMPRL1100] ... Command
COMMAND_TYPE_SCHEDULE_ACTIVITY_TASK doesn't match event
EVENT_TYPE_ACTIVITY_TASK_SCHEDULED ... expected value 'ActivityC' and an actual
value 'ActivityB'`. It does not fail loudly at deploy time. It fails later, on
the next Workflow Task, for executions you had forgotten about. The Workflow is
not failed or terminated: its Workflow Task keeps retrying, so a fix deployed
later lets it carry on.

Only executions that are still open are at risk. A Workflow that completed
before the deploy never replays again.
-->

---
layout: code-walkthrough
heading: "After: the versioned Workflow"
notes:
  - "<code>getVersion</code> marks one decision point, named by a change ID you choose"
  - New executions get version 1 and take this branch
  - Executions that started before the change get the default version and keep taking the old one
---

::code::

```java {all|5-6|7-8|9-10}
@Override
public void run() {
  activities.activityA();

  int version =
      Workflow.getVersion("use-activity-c", Workflow.DEFAULT_VERSION, 1);
  if (version == 1) {
    activities.activityC();
  } else {
    activities.activityB();
  }
}
```

<!--
Source: PPT slide 70.

Code: snippets/src/main/java/io/temporal/devdays/essentials/versioning/MyWorkflowImpl.java

Walk it carefully: this is the segment people most often half-understand.

- **Build 1 - `getVersion`.** `"use-activity-c"` is the change ID: a
  human-readable label for this one decision point. There is no global version
  number. Pick a name that says what changed. The two numbers are the oldest
  and newest versions this code still supports; `DEFAULT_VERSION` (-1) means
  "the code before any `getVersion` call".
- **Build 2 - the new branch.** An execution that reaches this line for the
  first time gets the newest version, 1, and records it.
- **Build 3 - the old branch.** Every execution already in flight when the
  deploy landed gets `DEFAULT_VERSION` and keeps taking B, because the SDK can
  see from its Event History that it never recorded a version.

The mechanism, in one sentence: on a new execution `getVersion` records a
`Version` marker in the Event History and returns `maxSupported`; on replay it
returns whatever the marker says, or `DEFAULT_VERSION` if there is none. The
branch taken is the branch that was always taken.

A Java detail if anyone asks: every `getVersion` call with the same change ID
returns the same version, so one change that touches several places in the
Workflow can share one ID. A later change to the same spot raises
`maxSupported` to 2 and adds a branch.
-->

---
layout: code-walkthrough
heading: Retiring the patch
notes:
  - "<code>getVersion</code> stays, but version 1 is now the only one it supports"
  - Only the new path is left. The <code class="ref">else</code> branch is gone
---

::code::

```java {all|5|6}
@Override
public void run() {
  activities.activityA();

  Workflow.getVersion("use-activity-c", 1, 1);
  activities.activityC();
}
```

<!--
Ported from the PPT slide 70 notes, which described these steps in prose.

Code: snippets/src/main/java/io/temporal/devdays/essentials/versioning/RetiredWorkflowImpl.java

The part people skip, and the reason this looks like ceremony until it does
not: the `if` is step one of three.

- **Build 1 - `minSupported` goes up to 1.** Deploy this once no execution
  that started before the patch is still running. It replays an execution that
  recorded version 1. An execution with no marker now fails its Workflow Task
  at this line with `Version -1 of changeId use-activity-c is not supported.
  Supported v is between 1 and 1`, instead of quietly running C. That loud
  failure is why the SDK recommends keeping the call.
- **Build 2 - the old branch is gone.** Only C remains.

Step three is optional in Java: once every execution that recorded the marker
has finished, you may delete the `getVersion` line too. The tests behind this
Deck show the Java SDK replays an execution with a `Version` marker that no
`getVersion` call asks for. What you give up is the guard from Build 1, and the
change ID: reuse `"use-activity-c"` later and it would read the old marker, so a
new change needs a new ID.

Why the order matters, and the tests behind this Deck show it: deploying this
too early fails any execution still on the B branch.

How to know when to move on: list open executions of the type in the Web UI or
with `temporal workflow list`, and wait for the old ones to finish. Leave the
`if` in forever and you have built a museum of every change you ever made.
-->

---
layout: code-walkthrough
heading: Catch it before you deploy
notes:
  - Fetch the Event History of a real execution
  - Replay it against the new code, in process. No Activities run
  - It throws if the new code would decide differently
---

::code::

```java {all|1|5|3-5}
WorkflowExecutionHistory history = client.fetchHistory(workflowId);

// Throws if the new code would make
// different decisions from this history
WorkflowReplayer.replayWorkflowExecution(history, MyWorkflowImpl.class);
```

<!--
Code: snippets/src/main/java/io/temporal/devdays/essentials/versioning/VersioningReplay.java

The non-determinism error from three slides ago shows up in production, days
later. This moves it into CI.

- **Build 1 - fetch.** Any execution will do; the useful ones are long-running
  executions that are open right now. You can also download a history as JSON
  from the Web UI or `temporal workflow show --output json`, check it in, and
  load it with `WorkflowExecutionHistory.fromJson` or
  `WorkflowReplayer.replayWorkflowExecutionFromResource`.
- **Build 2 - replay.** `WorkflowReplayer`, from `temporal-testing`, runs the
  Workflow implementation class against the recorded Events in an in-process
  test environment. Activities are not executed; their results come from the
  history.
- **Build 3 - the verdict.** It returns if the code makes the same decisions,
  and throws if it does not; the error names the `NonDeterministicException`.

Run it in a JUnit test against a handful of real histories and the unsafe edit
from the "Before" slide fails the build instead of the Workflow.
`replayWorkflowExecutions` takes many histories at once.
-->

---
layout: code-walkthrough
heading: Worker Versioning
notes:
  - The Worker joins a Worker Deployment
  - "<code>orders</code> names the deployment and stays the same. <code>2.0.0</code> changes with every build"
  - A <strong>pinned</strong> execution runs to completion on the version it started on
---

::code::

```java {all|3-5|6|7}
WorkerOptions options =
    WorkerOptions.newBuilder()
        .setDeploymentOptions(
            WorkerDeploymentOptions.newBuilder()
                .setUseVersioning(true)
                .setVersion(new WorkerDeploymentVersion("orders", "2.0.0"))
                .setDefaultVersioningBehavior(VersioningBehavior.PINNED)
                .build())
        .build();
Worker worker = factory.newWorker("orders", options);
```

<!--
Checked against the Java SDK 1.40 source. The tests behind this Deck start a
local dev server, make 2.0.0 current, and check that a new execution is pinned
to it.

Code: snippets/src/main/java/io/temporal/devdays/essentials/versioning/VersionedWorker.java

Patching changes the code so old and new executions can share one Worker.
Worker Versioning is the other approach: run old and new Workers side by side,
and let Temporal route each execution to the version it belongs to.

- **Build 1 - opt in.** `WorkerDeploymentOptions` on the `WorkerOptions`.
- **Build 2 - the version.** The deployment name (`orders`) names the service
  and stays constant; the build ID (`2.0.0`) names this build (a git hash, a
  semver, a build number).
- **Build 3 - the behaviour.** Required whenever versioning is on: either this
  default, or `@WorkflowVersioningBehavior` on each Workflow method. Without
  one, registering the Workflow throws. `PINNED` keeps an execution on the
  version it started on, so it never sees new code and never needs a patch.
  `AUTO_UPGRADE` moves it to the current version, which brings patching back.

A new version takes no new executions until you make it current:
`temporal worker deployment set-current-version --deployment-name orders
--build-id 2.0.0`. Existing pinned executions stay where they are, so the old
Workers keep running until those drain.

When to pick which: pinning suits short Workflows and frequent deploys. A
Workflow that runs for months would keep old Workers alive for months, so
long-running ones use patching, or Continue-As-New onto the new version.
-->
