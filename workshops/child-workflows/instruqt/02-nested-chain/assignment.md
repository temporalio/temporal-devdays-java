---
slug: nested-chain
id: nnoi1i4lmrfv
type: challenge
title: 2. A Deeply Nested Chain
teaser: Children can start children. Go five levels deep, fail the bottom one, and
  find out why from the top.
notes:
- type: text
  contents: |-
    # The bottom of a five-level chain fails. What does the top see?

    A child can start its own children, as deep as you like. Every level is a
    full Workflow Execution with its own history.

    When the bottom one fails, every parent above it fails too. The real reason
    is five clicks away.
tabs:
- id: k1sijra0mdvj
  title: Exercise
  type: service
  hostname: workshop
  path: /?folder=/root/workshop/child-workflows
  port: 8080
- id: ikwmhpuqnfhx
  title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/child-workflows/exercise
- id: wr5dimr50uwl
  title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/child-workflows/exercise
- id: rny96ltwy4xk
  title: Temporal UI
  type: service
  hostname: workshop
  path: /
  port: 8233
difficulty: basic
timelimit: 300
enhanced_loading: null
---

# Goal

Run a chain of Workflows five levels deep where the bottom one fails, and
follow the failure from the top to its real cause.

**Why it matters:** every level you nest is one more hop between an alert on
the top Workflow and the error that caused it.

# Read the code

Nothing to change in this Challenge. Open `ChainWorkflowImpl.java` in the
[button label="Exercise" background="#444CE7"](tab-0) tab. `ChainWorkflow`
starts **itself** as a child, one level shallower each time, until it reaches
depth 0:

```java
ChainWorkflow below = Workflow.newChildWorkflowStub(
        ChainWorkflow.class,
        ChildWorkflowOptions.newBuilder()
                .setWorkflowId(Workflow.getInfo().getWorkflowId() + "/" + (request.depth() - 1))
                .build());
return below.run(new ChainRequest(request.depth() - 1, request.failAtBottom())) + 1;
```

Each child's ID is its parent's ID plus one segment, so the ID shows how deep
it is. At depth 0, with `--fail`, it throws an `ApplicationFailure` of type
`BottomOfChain`.

# Run it

Each Challenge opens fresh terminals, so start the Worker again. In the
[button label="Worker" background="#444CE7"](tab-1) tab:

```bash,run
run-worker
```

```bash,nocopy
Worker polling child-workflows. Ctrl+C to stop.
```

In the [button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter chain --fail
```

```bash,nocopy
Chain chain-03AB79 failed. The failure, one wrapper per level:

  ChildWorkflowFailure  from chain-03AB79/4
  ChildWorkflowFailure  from chain-03AB79/4/3
  ChildWorkflowFailure  from chain-03AB79/4/3/2
  ChildWorkflowFailure  from chain-03AB79/4/3/2/1
  ChildWorkflowFailure  from chain-03AB79/4/3/2/1/0
  ApplicationFailure: BottomOfChain: the bottom of the chain failed
```

One failure, wrapped five times. Each parent that does not catch its child's
`ChildWorkflowFailure` fails with it, and adds another wrapper.

# Follow it in the UI

Open the [button label="Temporal UI" background="#444CE7"](tab-3) tab. Six
Workflows failed, one per level. If they aren't listed, click the refresh
button.

Click the top one, `chain-...` with no `/` in its ID. Its history ends with
**Child Workflow Execution Failed**. The reason it shows is a child failure,
not the cause. Click through to the child, and its child, until you reach
`.../0`, where the `ApplicationFailure` was thrown.

That is the cost of depth: five hops to read one error. **Keep hierarchies
shallow.** Use a child when the sub-process needs its own history, not to make
the code look tidier.

# Checkpoint

Click **Check**. It passes once a five-level chain has failed in the Temporal
dev server. If it fails, the message says what to run.
