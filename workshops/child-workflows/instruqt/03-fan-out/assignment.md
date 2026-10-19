---
slug: fan-out
id: pxde8lskzrak
type: challenge
title: 3. Fan Out
teaser: Start one child per item, all at once, then close the parent without waiting.
  Watch every child get terminated.
notes:
- type: text
  contents: |-
    # The parent starts five children and returns. What happens to them?

    Calling a child stub's method waits for the child to finish. To run five
    at once, start each one with `Async.function`: it returns a `Promise` at
    once, and the child runs on its own.

    This parent then returns before any child finishes. Watch what the default
    Parent Close Policy does to them.
tabs:
- id: phua2qeyqhge
  title: Exercise
  type: service
  hostname: workshop
  path: /?folder=/root/workshop/child-workflows
  port: 8080
- id: nky0apiigh4b
  title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/child-workflows/exercise
- id: p4vmiogvjxi8
  title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/child-workflows/exercise
- id: 9fpk0i6utahz
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

Fan out five children, close the parent without waiting for them, and see
what happens to the children.

**Why it matters:** a parent that returns early does not leave its children
running. By default it takes them down with it, and the work is lost.

# Where

<!-- snippet: exercise/src/main/java/io/temporal/devdays/childworkflows/PackWorkflowImpl.java TODO 1 before=12 -->
Nothing to change yet. Open `PackWorkflowImpl.java` in the
[button label="Exercise" background="#444CE7"](tab-0) tab. Press `Ctrl+P`
(`Cmd+P` on a Mac), type the file name, then `Ctrl+G` to jump to a line.
Lines 13-30 look like this:

```java
        // Fan out: start one child per item, without waiting for any of them.
        List<Promise<String>> boxes = new ArrayList<>();
        for (int i = 0; i < order.items().size(); i++) {
            PackItemWorkflow child = Workflow.newChildWorkflowStub(
                    PackItemWorkflow.class,
                    ChildWorkflowOptions.newBuilder()
                            .setWorkflowId("pack-" + order.orderId() + "-" + i)
                            .build());
            boxes.add(Async.function(child::pack, order.items().get(i)));
            Workflow.getWorkflowExecution(child).get(); // waits for the start, not the result
        }

        // TODO 1 start
        // Fan in: wait for every child with Promise.allOf(boxes), then return
        // each child's box, in item order. Until you do, the parent returns at
        // once and the default Parent Close Policy terminates every child.
        return List.of();
        // TODO 1 end
```

`Async.function` starts each `PackItemWorkflow` and returns a `Promise` for its
box straight away. `Workflow.getWorkflowExecution(child).get()` waits only
until the child has **started**, so the loop starts all five back to back.
Each child sleeps 3 seconds and returns a box. Then the parent reaches TODO 1
and returns an empty list.

Leave TODO 1 as it is for now. It is the next Challenge.

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
run-starter pack
```

```bash,nocopy
Packed 0 of 5 items in 0.3s: []
```

Zero boxes. Open the newest `pack-ORD-...` Workflow in the
[button label="Temporal UI" background="#444CE7"](tab-3) tab. If it isn't
there, click the refresh button: the list does not always update on its own.
Five **Start Child Workflow Execution Initiated** events, each followed by
**Child Workflow Execution Started**, then the parent **completed**.

Go back to the Workflow list. The five children, `pack-ORD-...-0` to `-4`, are
all **Terminated**.

The parent closed while its children were still running. The default Parent
Close Policy, `TERMINATE`, ended every one of them. Starting children is only
half the pattern.

# Checkpoint

Click **Check**. It passes once five children have been terminated this way.
If it fails, the message says what to run.
