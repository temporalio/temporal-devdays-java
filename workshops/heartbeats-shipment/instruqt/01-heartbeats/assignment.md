---
slug: heartbeats
id: himdbxz87yku
type: challenge
title: 1. Activity Heartbeats
teaser: Crash a Worker mid-job and watch the retry wait, then start over. Add Heartbeats
  and watch it pick up in seconds, where it left off.
notes:
- type: text
  contents: |-
    # The Worker dies 10 items into a 40 item job. Now what?

    Packing a shipment takes a while. Halfway through, the process doing it
    disappears: a deploy, an out-of-memory kill, a lost node.

    How long before Temporal notices? And where does the retry start?

    **Author:** [Nikolay Advolodkin](https://www.linkedin.com/in/nikolayadvolodkin/), Staff Developer Advocate
- type: text
  contents: |-
    # Three words for this lab

    **Heartbeat.** A call an Activity makes while it works: "still here, and
    this is how far I got."

    **Heartbeat Timeout.** How long Temporal waits between Heartbeats before it
    treats the attempt as lost and retries it.

    **Heartbeat details.** The progress sent with the last Heartbeat. The next
    attempt can read it back.
tabs:
- id: ykgz5qdhuh8f
  title: Exercise
  type: service
  hostname: workshop
  path: /?folder=/root/workshop/heartbeats-shipment
  port: 8080
- id: xk9ykpskwykn
  title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/heartbeats-shipment/exercise
- id: m9qakdskadb9
  title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/heartbeats-shipment/exercise
- id: wswsa5lajlp5
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

Kill a Worker in the middle of a packing job, without Heartbeats and then with
them, and compare how long the retry takes to start and where it starts.

**Why it matters:** without Heartbeats, a crash holds the work until the whole
attempt times out, and the retry redoes every item. In a real system that is
lost time and duplicated side effects.

# Your tabs

- [button label="Exercise" background="#444CE7"](tab-0) VS Code in the browser, open on this Workshop. You edit
  the code under `exercise/`. It saves as you type.
- [button label="Worker" background="#444CE7"](tab-1) a terminal in `exercise/`. Run `run-worker` here. It
  restarts the Worker every time you save.
- [button label="Client" background="#444CE7"](tab-2) a terminal in `exercise/`. Run `run-starter` here to start
  a Workflow.
- [button label="Temporal UI" background="#444CE7"](tab-3) the Temporal Web UI of the local dev server:
  the shipment Workflow, each packing attempt, and the Heartbeat details it saved.

# Run it without Heartbeats

`packShipment` in `ShipmentActivitiesImpl.java` packs 40 items, one every 0.3
seconds, about 12 seconds in all. `ShipmentWorkflowImpl.java` gives each attempt
a 20-second start-to-close timeout.

In the [button label="Worker" background="#444CE7"](tab-1) tab:

```bash,run
run-worker
```

```bash,nocopy
Shipment Worker polling shipment-tasks. Ctrl+C to crash it.
```

In the [button label="Client" background="#444CE7"](tab-2) tab, start the job:

```bash,run
run-starter
```

Switch to the [button label="Worker" background="#444CE7"](tab-1) tab and watch
it count: `packed item 1 of 40`, `packed item 2 of 40`. Around item 10, press
**Ctrl+C**. The Worker exits on the spot, with no chance to tell Temporal, the
way a crashed node does.

Open `shipment-demo` in the [button label="Temporal UI" background="#444CE7"](tab-3)
tab (click refresh if it isn't listed) and look at **Pending Activities**:
attempt 1, still **Started**, and no Heartbeat. Temporal has no idea the Worker
is gone.

Start a replacement in the [button label="Worker" background="#444CE7"](tab-1) tab:

```bash,run
run-worker
```

It polls and packs nothing. About 18 seconds after the crash, once the first
attempt's 20 seconds run out, it prints:

```bash,nocopy
17:16:19 INFO ShipmentActivitiesImpl - packing shipment-demo, attempt 2, starting at item 0
```

**Slow, and from item 0.** The items the crashed Worker had already packed get
packed a second time.

# Where

Three short edits, in two files.

<!-- snippet: exercise/src/main/java/io/temporal/devdays/shipment/ShipmentWorkflowImpl.java TODO 1a before=5 -->
Open `ShipmentWorkflowImpl.java` in the
[button label="Exercise" background="#444CE7"](tab-0) tab. Press `Ctrl+P`
(`Cmd+P` on a Mac), type the file name, then `Ctrl+G` to jump to a line.
Lines 9-15 look like this:

```java
    private final ShipmentActivities activities = Workflow.newActivityStub(
            ShipmentActivities.class,
            ActivityOptions.newBuilder()
                    // A ceiling for the whole shipment, which takes about 12 seconds.
                    .setStartToCloseTimeout(Duration.ofSeconds(20))
                    // TODO 1a start: give up on an attempt when no Heartbeat arrives for 2 seconds
                    // TODO 1a end
```

<!-- snippet: exercise/src/main/java/io/temporal/devdays/shipment/ShipmentActivitiesImpl.java TODO 1c before=4 -->
Then open `ShipmentActivitiesImpl.java`. Lines 14-20 look like this:

```java
    public ShipmentResult packShipment(ShipmentInput input) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        int attempt = context.getInfo().getAttempt();

        // TODO 1c start: start from the count the last attempt Heartbeated, or 0 on a first attempt
        int packed = 0;
        // TODO 1c end
```

<!-- snippet: exercise/src/main/java/io/temporal/devdays/shipment/ShipmentActivitiesImpl.java TODO 1b before=6 -->
And further down, lines 24-31:

```java
        try {
            for (int item = packed; item < input.itemCount(); item++) {
                packOneItem();
                packed = item + 1;
                log.info("packed item {} of {}", packed, input.itemCount());

                // TODO 1b start: Heartbeat the number of items packed so far
                // TODO 1b end
```

# Change it

- [ ] **1a**, `ShipmentWorkflowImpl.java`: add a Heartbeat Timeout of 2 seconds
  to the `ActivityOptions`. If 2 seconds pass with no Heartbeat, Temporal treats
  the attempt as lost, and the retry options start attempt 2.
- [ ] **1b**, `ShipmentActivitiesImpl.java`: after each item, call
  `context.heartbeat(packed)`. That is the "still here" signal, and it carries
  the count.
- [ ] **1c**, `ShipmentActivitiesImpl.java`: instead of `packed = 0`, start from
  the count the last attempt sent. A retry finds it with
  `context.getHeartbeatDetails(Integer.class)`, an `Optional<Integer>`. On a
  first attempt it is empty, so start at 0.

Do all three before you run it. A Heartbeat Timeout with no Heartbeats makes
every attempt fail after 2 seconds, even a healthy one.

Change only the lines between each `// TODO 1x start` and `// TODO 1x end`. The
editor saves as you type, and the Worker restarts on every save.

<details>
<summary>Hint for 1c</summary>

The same shape, reading an optional setting with a fallback:

```java
int port = Optional.ofNullable(System.getenv("PORT")).map(Integer::parseInt).orElse(8080);
```

</details>

<details>
<summary>Show solution</summary>

<!-- snippet: solution/src/main/java/io/temporal/devdays/shipment/ShipmentWorkflowImpl.java TODO 1a before=5 -->
Lines 9-16 of `solution/.../ShipmentWorkflowImpl.java`:

```java
    private final ShipmentActivities activities = Workflow.newActivityStub(
            ShipmentActivities.class,
            ActivityOptions.newBuilder()
                    // A ceiling for the whole shipment, which takes about 12 seconds.
                    .setStartToCloseTimeout(Duration.ofSeconds(20))
                    // TODO 1a start: give up on an attempt when no Heartbeat arrives for 2 seconds
                    .setHeartbeatTimeout(Duration.ofSeconds(2))
                    // TODO 1a end
```

<!-- snippet: solution/src/main/java/io/temporal/devdays/shipment/ShipmentActivitiesImpl.java TODO 1c before=4 -->
Lines 14-20 of `solution/.../ShipmentActivitiesImpl.java`:

```java
    public ShipmentResult packShipment(ShipmentInput input) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        int attempt = context.getInfo().getAttempt();

        // TODO 1c start: start from the count the last attempt Heartbeated, or 0 on a first attempt
        int packed = context.getHeartbeatDetails(Integer.class).orElse(0);
        // TODO 1c end
```

<!-- snippet: solution/src/main/java/io/temporal/devdays/shipment/ShipmentActivitiesImpl.java TODO 1b before=6 -->
And lines 24-32:

```java
        try {
            for (int item = packed; item < input.itemCount(); item++) {
                packOneItem();
                packed = item + 1;
                log.info("packed item {} of {}", packed, input.itemCount());

                // TODO 1b start: Heartbeat the number of items packed so far
                context.heartbeat(packed);
                // TODO 1b end
```

</details>

# Run it with Heartbeats

The Worker restarted when you saved. If it is not running, start it in the
[button label="Worker" background="#444CE7"](tab-1) tab:

```bash,run
run-worker
```

Run the same experiment. In the
[button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter
```

Watch the [button label="Worker" background="#444CE7"](tab-1) tab and press
**Ctrl+C** around item 10.

Pending Activities in the [button label="Temporal UI" background="#444CE7"](tab-3)
tab now shows a last Heartbeat and its details, the count. Start a replacement
right away:

```bash,run
run-worker
```

As soon as it is up, a few seconds after the crash:

```bash,nocopy
17:17:21 INFO ShipmentActivitiesImpl - packing shipment-demo, attempt 2, starting at item 6
```

And the Client tab finishes with:

```bash,nocopy
Packed 40 items, finished on attempt 2.
An earlier attempt was interrupted. This one finished the job.
```

**Fast, and from where it left off.** Your number depends on when you killed it.

It resumes a few items behind the last one the dead Worker printed. The SDK
**throttles** Heartbeats: with a 2-second timeout it sends at most one every
1.6 seconds and keeps only the newest in between. A crash sends nothing more,
so whatever was packed after the last Heartbeat left the process is packed
again. Heartbeat as often as you like; the SDK decides what goes over the wire.

# Checkpoint

Click **Check**. It passes when a silent attempt is retried within seconds,
every item is Heartbeated, and a retry resumes from the last Heartbeat. If it
fails, the message names the file and the part still to do.

One more thing Heartbeats carry: **cancellation**. Start a job again:

```bash,run
run-starter
```

While it packs, open `shipment-demo` in the
[button label="Temporal UI" background="#444CE7"](tab-3) tab and choose
**Request Cancellation** from the Workflow's actions, top right. The Worker
prints `interrupted at item N`: the next `heartbeat()` call throws an
`ActivityCompletionException`. The Client tab says the shipment ended Canceled.
An Activity that never Heartbeats never learns it was cancelled, and keeps
working.
