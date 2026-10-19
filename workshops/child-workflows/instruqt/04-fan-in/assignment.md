---
slug: fan-in
id: pfkvxk7wsif8
type: challenge
title: 4. Fan In
teaser: Wait for every child and collect the results. Five 3-second children, done
  in about 3 seconds.
notes:
- type: text
  contents: |-
    # Five children, three seconds each. How long does the parent take?

    Every child you started in the last Challenge left a `Promise` behind.
    Wait on all of them together and the parent stays open until every child
    is done, while they all run at the same time.
tabs:
- id: wnizhlzjjhak
  title: Exercise
  type: service
  hostname: workshop
  path: /?folder=/root/workshop/child-workflows
  port: 8080
- id: 5q2hncryz50l
  title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/child-workflows/exercise
- id: ho8jvkwiv8yr
  title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/child-workflows/exercise
- id: ef5heuv70kwj
  title: Temporal UI
  type: service
  hostname: workshop
  path: /
  port: 8233
difficulty: basic
timelimit: 600
enhanced_loading: null
---

# Goal

Make `PackWorkflowImpl.pack` wait for every child and return their boxes, so
no child is terminated and the order comes back fully packed.

**Why it matters:** the order is only packed when every box is. A parent that
returns early ships an empty order and throws the packing work away.

# Where

<!-- snippet: exercise/src/main/java/io/temporal/devdays/childworkflows/PackWorkflowImpl.java TODO 1 -->
Open `PackWorkflowImpl.java` in the [button label="Exercise" background="#444CE7"](tab-0)
tab. Press `Ctrl+P` (`Cmd+P` on a Mac), type the file name, then `Ctrl+G` to jump to a line.
Lines 25-30 look like this:

```java
        // TODO 1 start
        // Fan in: wait for every child with Promise.allOf(boxes), then return
        // each child's box, in item order. Until you do, the parent returns at
        // once and the default Parent Close Policy terminates every child.
        return List.of();
        // TODO 1 end
```

`boxes` is the `List<Promise<String>>` the fan-out loop above filled, one
`Promise` per child, in item order.

# Change it

- [ ] Wait for every child with `Promise.allOf(boxes)`.
- [ ] Return each child's box, in item order, instead of `List.of()`.

Change only the lines between `// TODO 1 start` and `// TODO 1 end`. Leave the
fan-out loop as it is: it starts every child before you wait for any.

<details>
<summary>Hint</summary>

`Promise.allOf` returns a `Promise` that is ready once every `Promise` in the
list is. After that, each `get()` returns at once:

```java
List<Promise<Integer>> sizes = ...;
Promise.allOf(sizes).get();            // blocks until every Promise is ready
int total = sizes.get(0).get() + sizes.get(1).get();
```

</details>

<details>
<summary>Show solution</summary>

```java
        Promise.allOf(boxes).get();
        return boxes.stream().map(Promise::get).toList();
```

</details>

# Run it

Each Challenge opens fresh terminals, so start the Worker again. It restarts
on its own every time you save. In the
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
Packed 5 of 5 items in 3.5s: [box:mug, box:book, box:lamp, box:plant, box:clock]
```

Five children of 3 seconds each, in about 3 seconds, not 15. They ran in
parallel.

Open the newest `pack-ORD-...` Workflow in the
[button label="Temporal UI" background="#444CE7"](tab-3) tab. If it isn't
there, click the refresh button. All five **Start Child Workflow Execution
Initiated** events come first, then five **Child Workflow Execution
Completed**. This time every child is **Completed**, none Terminated.

# What you saw

- One child, waited for by calling its stub, with its own history.
- A chain five deep, where one error came up through five wrappers.
- A fan out with `Async.function`, and `TERMINATE` ending the children the
  parent did not wait for.
- A fan in with `Promise.allOf`, five children in the time of one.

# Checkpoint

Click **Check**. It passes when TODO 1's tests pass and five children have
completed under one parent in the Temporal dev server. If it fails, the message
names the file and the TODO still to do, or what to run.
