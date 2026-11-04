---
layout: section
---

<ZiggyHero scene="poll">

# TypeScript Workers

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
heading: TypeScript Workers
notes:
  - "Activities are imported normally: this file is ordinary Node, not the Workflow isolate"
  - Connects to a Temporal Server at <code>localhost:7233</code>. This is the Worker's connection
  - Polls one specific Task Queue. It must match the Task Queue the Client starts Workflows on
  - Workflows are registered by <strong>file path</strong>, not by reference
  - Registers the Activity functions this Worker can run
  - Starts the Worker. Runs until shut down, polling for work
---

::code::

```ts {all|3|6-8|11|12|13|15}
// worker.ts
import { NativeConnection, Worker } from "@temporalio/worker";
import * as activities from "./activities";

async function run() {
  const connection = await NativeConnection.connect({
    address: "localhost:7233",
  });
  const worker = await Worker.create({
    connection,
    taskQueue: "order-task-queue",
    workflowsPath: require.resolve("./workflows"),
    activities,
  });
  await worker.run();
}

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
```

<!--
Source: PPT slides 63-70.
Code: snippets/src/workers-anatomy.worker.ts (type-checked; the runtime tests
build the same Worker against a test server).

The original had seven steps: service stubs, Client, factory, Worker, register
Workflows, register Activities, start. TypeScript folds them into one
`NativeConnection.connect` and one `Worker.create(...)`.

- **Build 1 - a normal import.** Contrast with the Workflow file, which could
  only take the *type*. The Worker process is ordinary Node, so it imports the
  real implementations.
- **Build 2 - the connection.** `NativeConnection` is the Worker's connection,
  separate from the `Client` your application uses to start Workflows. Leave
  `connection` out and the Worker connects to `localhost:7233` on its own. For
  Temporal Cloud you add `apiKey` or `tls` here and a `namespace` on the Worker;
  the rest of the file does not change.
- **Build 3 - the Task Queue.** A name, nothing more. The Client puts Tasks on
  it, the Worker takes them off. Typo it on one side and the Workflow starts
  fine and waits forever with no error. Say that out loud; it is the most common
  first-day bug.
- **Build 4 - `workflowsPath`.** The one genuinely surprising line. Workflows
  are registered by path, not by value, because the Worker bundles that file and
  everything it imports, then runs the bundle in a deterministic V8 isolate.
  Each Workflow Execution gets its own copy of the module's globals, so one
  run's state never leaks into another. `require.resolve` resolves relative to
  this file, not the working directory; in an ES module, write
  `new URL("./workflows.ts", import.meta.url)` and convert it to a path.
- **Build 5 - Activities.** A plain object; the keys are the Activity Types.
  `import * as activities` registers every export under its own name. If your
  Activities need a shared HTTP client or database pool, build them with a
  factory function that closes over it and pass the result here. The Worker only
  runs Activities it was given.
- **Build 6 - `run()`.** Long-polls until shut down. The SDK listens for
  `SIGINT` and `SIGTERM` by default, so Ctrl-C or a container stop starts a
  graceful shutdown and `run()` resolves. Set `shutdownGraceTime` on the Worker
  so in-flight Activities get time to finish; it defaults to zero, which
  cancels them straight away.

If asked about scale: run as many Workers on this Task Queue as you like. They
are stateless and interchangeable. That is the whole horizontal-scaling story.
-->
