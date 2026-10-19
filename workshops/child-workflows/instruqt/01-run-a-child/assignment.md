---
slug: run-a-child
id: vjqclyn4i5ee
type: challenge
title: 1. Run a Child
teaser: One Workflow starts another and waits for its result. Find both in the Temporal
  UI.
notes:
- type: text
  contents: |-
    # What do you get when a Workflow starts a Workflow?

    Not a method call, and not an Activity. A second Workflow Execution, with
    its own Workflow ID, its own Event History and its own retries. The parent
    can wait for its result, or start it and move on.

    **Author:** [Nikolay Advolodkin](https://www.linkedin.com/in/nikolayadvolodkin/), Staff Developer Advocate
- type: text
  contents: |-
    # Three words for this lab

    **Child Workflow.** A Workflow started by another Workflow, the parent.

    **Fan out.** Start many children at once, without waiting for any of them.

    **Fan in.** Wait for all of them and collect their results.
tabs:
- id: ytoknrge6cob
  title: Exercise
  type: service
  hostname: workshop
  path: /?folder=/root/workshop/child-workflows
  port: 8080
- id: xm6xexjm1khf
  title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/child-workflows/exercise
- id: ey2voisgo85a
  title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/child-workflows/exercise
- id: 5sndecaa71ql
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

Run an order whose Workflow starts a shipment Workflow as its child, and find
the two separate histories in the Temporal UI.

**Why it matters:** a child is a full Workflow Execution. It can be found,
retried and read on its own, apart from the order that started it.

# Your tabs

- [button label="Exercise" background="#444CE7"](tab-0) VS Code in the browser, open on this Workshop. You edit
  the code under `exercise/`. It saves as you type.
- [button label="Worker" background="#444CE7"](tab-1) a terminal in `exercise/`. Run `run-worker` here. It
  restarts the Worker every time you save.
- [button label="Client" background="#444CE7"](tab-2) a terminal in `exercise/`. Run `run-starter` here to start
  a Workflow.
- [button label="Temporal UI" background="#444CE7"](tab-3) the Temporal Web UI of the local dev server:
  every order Workflow and the child Workflows it started, each with its own Event History.

# Read the code

Nothing to change in this Challenge. Open `OrderWorkflowImpl.java` in the
[button label="Exercise" background="#444CE7"](tab-0) tab to read the parent.
Press `Ctrl+P` (`Cmd+P` on a Mac) and type the file name. Its whole body is a
child stub and one call:

```java
ShipmentWorkflow shipment = Workflow.newChildWorkflowStub(
        ShipmentWorkflow.class,
        ChildWorkflowOptions.newBuilder()
                .setWorkflowId("shipment-" + order.orderId())
                .build());
return shipment.ship(order.orderId());
```

`Workflow.newChildWorkflowStub` gives the child its own Workflow ID. Calling
`ship` starts the child and waits for its result.

# Run it

In the [button label="Worker" background="#444CE7"](tab-1) tab:

```bash,run
run-worker
```

```bash,nocopy
Worker polling child-workflows. Ctrl+C to stop.
```

Leave it running. Each Challenge opens fresh terminals, so you start it again
at the top of every one.

In the [button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter child
```

```bash,nocopy
Parent  order-ORD-701297  completed
Child   shipment-ORD-701297  returned TRACK-ORD-701297
```

# Find both Workflows

Open the [button label="Temporal UI" background="#444CE7"](tab-3) tab. Two
Workflows are listed, not one: `order-ORD-...` and `shipment-ORD-...`. If they
aren't there, click the refresh button: the list does not always update on its
own.

Click `order-ORD-...` and read its Event History:

| Event | What it means |
|---|---|
| **Start Child Workflow Execution Initiated** | the parent asked for a child |
| **Child Workflow Execution Started** | the child exists, with its own ID |
| **Child Workflow Execution Completed** | the child returned `TRACK-...` |

Click the child's link in those events. The shipment has a history of its own,
with the 2 second timer the parent never saw. Its page names the parent.

# Checkpoint

Click **Check**. It passes once a shipment child has completed in the Temporal
dev server. If it fails, the message says what to run.
