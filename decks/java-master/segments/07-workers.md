---
layout: section
---

<ZiggyHero scene="poll">

# Java Workers

The process that polls. Nothing runs without one.

</ZiggyHero>

<!--
Source: PPT slides 62-70.

Worth saying before the code: the Worker is the only part of Temporal that runs
your code. The Server never sees it. That is the answer to the security question
somebody always asks.
-->

---
layout: code-walkthrough
heading: Java Workers
notes:
  - "Connects to a Temporal Server at <code>localhost:7233</code>: the gRPC service stubs"
  - The <code>WorkflowClient</code>. A Worker talks to the Server through an ordinary Client
  - The <code>WorkerFactory</code> owns the Workers and their thread pools
  - Polls one specific Task Queue. It must match the Task Queue the Client starts Workflows on
  - "Workflows are registered by <strong>class</strong>: one new instance per Workflow Execution"
  - "Activities are registered as an <strong>instance</strong>, shared by every Activity Task"
  - Starts polling. Runs until the factory is shut down
---

::code::

```java {all|4-5|6|7|9|10|11|13}
// WorkerApp.java
public class WorkerApp {
  public static void main(String[] args) {
    WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget("localhost:7233").build());
    WorkflowClient client = WorkflowClient.newInstance(service);
    WorkerFactory factory = WorkerFactory.newInstance(client);

    Worker worker = factory.newWorker("transfers");
    worker.registerWorkflowImplementationTypes(TransferWorkflowImpl.class);
    worker.registerActivitiesImplementations(new BankActivitiesImpl());

    factory.start();
  }
}
```

<!--
Source: PPT slides 63-70.
Code: snippets/src/main/java/io/temporal/devdays/essentials/workers/WorkerApp.java
(compiled, never run: it would poll localhost:7233 forever). WorkerAppTest builds
the same Worker against the test server.

The original had seven steps: service stubs, Client, factory, Worker, register
Workflows, register Activities, start. Java keeps all seven, one line each.

- **Build 1 - the connection.** `WorkflowServiceStubs` is the gRPC connection to
  the Temporal Service. `WorkflowServiceStubs.newLocalServiceStubs()` is the
  shortcut for `127.0.0.1:7233`. For Temporal Cloud you set the target, TLS or
  an API key on these options and a namespace on the Client; the rest of the
  file does not change.
- **Build 2 - the Client.** The same `WorkflowClient` class your application
  uses to start Workflows; the Worker polls through it. One per process.
- **Build 3 - the factory.** `WorkerFactory` creates Workers and owns the
  threads they poll and run on. One factory can host Workers for several Task
  Queues.
- **Build 4 - the Task Queue.** A name, nothing more. The Client puts Tasks on
  it, the Worker takes them off. Typo it on one side and the Workflow starts
  fine and waits forever with no error. Say that out loud; it is the most common
  first-day bug.
- **Build 5 - Workflows by class.** The one genuinely surprising line. You pass
  the implementation *class*, not an object, because the Worker creates a fresh
  instance for every Workflow Execution, and rebuilds it by replay when it needs
  to. One run's fields never leak into another.
- **Build 6 - Activities by instance.** The opposite: one object you construct,
  shared by every Activity Task this Worker runs, on many threads at once. That
  is where a shared HTTP client or database pool goes: pass it to the
  constructor. Keep the class thread-safe. The Worker only runs Activities it
  was given.
- **Build 7 - `factory.start()`.** Returns straight away; the Workers poll on
  their own threads, which keep the JVM alive. The SDK installs no shutdown hook,
  so for a clean stop add one that calls `factory.shutdown()` and then
  `factory.awaitTermination(...)`: polling stops and Tasks already received are
  allowed to finish. `shutdownNow()` interrupts them instead.

If asked about scale: run as many Workers on this Task Queue as you like. They
are stateless and interchangeable. That is the whole horizontal-scaling story.
-->
