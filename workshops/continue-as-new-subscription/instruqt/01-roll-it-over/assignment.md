---
slug: roll-it-over
id: tbxpyrh46aok
type: challenge
title: 1. Continue-As-New
teaser: A subscription bills every cycle until the Temporal Service terminates it.
  Add Continue-As-New and watch it finish.
notes:
- type: text
  contents: |-
    # What happens to a Workflow that never stops growing?

    A subscription bills its customer every cycle. Every charge and every timer
    appends to one Event History, and that history has a hard ceiling.

    Past it, the Temporal Service terminates the Workflow. Long before that, it
    tells the Workflow to roll over. Does anything listen?

    **Author:** [Nikolay Advolodkin](https://www.linkedin.com/in/nikolayadvolodkin/), Staff Developer Advocate
- type: text
  contents: |-
    # Three words for this lab

    **Event History.** The append-only record of everything a Workflow Execution
    did. It has a limit on the number of Events.

    **Continue-As-New.** Close this run and start a fresh one atomically: same
    Workflow Id, new Run Id, new short history.

    **Run.** One link in that chain. The next run knows only the input it is
    handed.
tabs:
- id: yfkygfwhqyyt
  title: Exercise
  type: service
  hostname: workshop
  path: /?folder=/root/workshop/continue-as-new-subscription
  port: 8080
- id: 1p9pouu3bth0
  title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/continue-as-new-subscription/exercise
- id: jryxrfwfukr5
  title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/continue-as-new-subscription/exercise
- id: atp2grqnlyyv
  title: Temporal UI
  type: service
  hostname: workshop
  path: /
  port: 8233
difficulty: advanced
timelimit: 600
enhanced_loading: null
---

# Goal

Run a 30-cycle subscription and watch the Temporal Service terminate it. Then
add Continue-As-New so it rolls over when the Service suggests it, and finishes.

**Why it matters:** any Workflow that lives for months, such as a subscription,
a policy that renews every year, or an account, keeps appending to its history.
Without Continue-As-New it is eventually terminated, mid-flight.

The blue buttons are clickable. Each one jumps to a tab.

# The limits

The Temporal Service checks every Event History against these limits:

| | Normally | In this lab |
|---|---|---|
| Suggests Continue-As-New | 4,096 Events or 4 MB | 40 Events |
| Terminates the Workflow | 51,200 Events or 50 MB | 200 Events |

This lab's dev server lowers both so you can watch them in seconds. One billing
cycle adds about 11 Events.

# Run it without Continue-As-New

In the [button label="Worker" background="#444CE7"](tab-1) tab:

```bash,run
run-worker
```

```bash,nocopy
Subscription Worker polling subscription-tasks. Ctrl+C to stop.
```

Leave it running. It recompiles and restarts every time you save a file.

In the [button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter
```

The Worker counts cycles. About 20 seconds later the Client tab prints:

```bash,nocopy
Billing subscription-demo for 30 cycles. Waiting for it to finish...

subscription-demo was terminated by the Temporal Service: Workflow history count exceeds limit.
```

The Worker stopped at cycle 18 of 30.

Open the [button label="Temporal UI" background="#444CE7"](tab-3) tab and click
`subscription-demo`. If it isn't listed, click the refresh button: the list does
not always update on its own. It is **Terminated**, and its history ends at
Event 201 with `WorkflowExecutionTerminated`.

Now click any **Workflow Task Started** event after Event 40. It reads
**Suggest Continue As New: true**. The Service suggested a rollover on every
Workflow Task from then on, and nothing in the code listened.

# Where

<!-- snippet: exercise/src/main/java/io/temporal/devdays/subscription/SubscriptionWorkflowImpl.java TODO 1 before=6 -->
Open `SubscriptionWorkflowImpl.java` in the
[button label="Exercise" background="#444CE7"](tab-0) tab. Press `Ctrl+P`
(`Cmd+P` on a Mac), type the file name, then `Ctrl+G` to jump to a line.
Lines 30-37 look like this:

```java
    public SubscriptionResult run(SubscriptionInput input) {
        while (maxCycles == 0 || cyclesCompleted < maxCycles) {
            activities.chargeSubscription(subscriptionId, cyclesCompleted + 1);
            cyclesCompleted++;
            Workflow.sleep(Shared.CYCLE);

            // TODO 1 start: when the Service suggests it, continue as new with what this run has done
            // TODO 1 end
```

# Change it

- [ ] After each cycle, check `Workflow.getInfo().isContinueAsNewSuggested()`.
- [ ] When it is `true`, call `Workflow.continueAsNew(...)` with a
  `SubscriptionInput` that carries what this run has done:
  `subscriptionId`, `cyclesCompleted` and `maxCycles`.

The next run knows only the input you hand it. Pass `cyclesCompleted`, or every
new run starts counting from 0 again and the subscription never finishes.

Change only the lines between `// TODO 1 start` and `// TODO 1 end`.

<details>
<summary>Hint</summary>

`continueAsNew` takes the next run's arguments, the same way the starter passes
the first run's. `SubscriptionInput` is a record:

```java
Workflow.continueAsNew(new SubscriptionInput(/* subscriptionId, cyclesCompleted, maxCycles */));
```

It never returns: the current run closes on that line. The next run's
constructor, marked `@WorkflowInit`, reads the input you passed.

</details>

<details>
<summary>Show solution</summary>

<!-- snippet: solution/src/main/java/io/temporal/devdays/subscription/SubscriptionWorkflowImpl.java TODO 1 before=6 -->
Lines 30-40 of `solution/.../SubscriptionWorkflowImpl.java`:

```java
    public SubscriptionResult run(SubscriptionInput input) {
        while (maxCycles == 0 || cyclesCompleted < maxCycles) {
            activities.chargeSubscription(subscriptionId, cyclesCompleted + 1);
            cyclesCompleted++;
            Workflow.sleep(Shared.CYCLE);

            // TODO 1 start: when the Service suggests it, continue as new with what this run has done
            if (Workflow.getInfo().isContinueAsNewSuggested()) {
                Workflow.continueAsNew(new SubscriptionInput(subscriptionId, cyclesCompleted, maxCycles));
            }
            // TODO 1 end
```

</details>

# Run it again

Save. The [button label="Worker" background="#444CE7"](tab-1) tab recompiles
and restarts the Worker. In the
[button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter
```

About 30 seconds later:

```bash,nocopy
Billing subscription-demo for 30 cycles. Waiting for it to finish...

Done. Billed 30 cycles.
```

The Worker counts straight from cycle 1 to cycle 30.

Refresh the [button label="Temporal UI" background="#444CE7"](tab-3) tab. One
Workflow Id, `subscription-demo`, now has a chain of runs: each closed run is
**Continued as New**, and the last is **Completed**. Open any of them: each
history is short, and each run after the first starts with the
`cyclesCompleted` the run before it handed over.

**Same Workflow Id, new Run Id, fresh Event History.**

# Checkpoint

Click **Check**. It passes when a subscription rolls over on the Service's
suggestion and still bills every cycle it was asked for. If it fails, the
message names the file and the TODO still to do.

Before you ship this pattern:

- The input you hand over is a payload, limited to 2 MB. Carry ids and counters,
  not large data.
- Clients that address the Workflow Id follow the chain to the newest run, which
  is why the starter's `getResult` waited across every rollover.
- If the Workflow has Signal or Update handlers, wait for them to finish first,
  with `Workflow.await(Workflow::isEveryHandlerFinished)`, so a rollover
  does not cut one off halfway.
