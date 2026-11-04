---
layout: section
---

<ZiggyHero scene="bridge">

# TypeScript Clients

How your application talks to Temporal.

</ZiggyHero>

<!--
New to the TypeScript Deck. The Workers section showed the process that runs
the code; this one shows the code that asks for it to run.
-->

---
layout: default
---

# What is a Client?

Your application's way in to Temporal.

- **Starts Workflows**, and Signals, Queries, Updates or cancels running ones
- **Talks to the Temporal Service**, never to a Worker directly
- **Runs anywhere**: an API handler, a CLI, a cron job, another service
- **Runs none of your Workflow or Activity code.** Workers do

<!--
New to the TypeScript Deck.

The picture to leave in the room: the Client and the Worker never meet. The
Client asks the Service to start a Workflow; the Service puts a Task on the
Task Queue; a Worker polling that queue picks it up. Either side can be down
while the other carries on, which is why a Client can start a Workflow even if
no Worker is running yet. It simply waits on the queue.

The Client lives in `@temporalio/client`, the third of the four SDK packages.
It is plain Node.js, so it goes wherever your application already runs.
-->

---
layout: code-walkthrough
heading: TypeScript Client
notes:
  - The Client package. Plain Node.js, like the Worker
  - "Imports the Workflow function for its <strong>name and types</strong>. The Client never runs it"
  - "Connects to the Temporal Service. <code>Connection</code>, a different class from the Worker's"
  - The Client. Create one per process and share it
  - Starts the Workflow and waits for its result
  - Must match the Task Queue the Worker polls
  - "The business ID is the Workflow ID. While it runs, a second start with it is rejected"
  - The input, type-checked against the Workflow's parameters
  - The receipt, typed from the Workflow's return value
---

::code::

```ts {all|2|4|7|8|16|17|18|19|21}
// client.ts
import { Client, Connection } from "@temporalio/client";
import type { Transfer } from "./models";
import { transferWorkflow } from "./workflows";

export async function run() {
  const connection = await Connection.connect({ address: "localhost:7233" });
  const client = new Client({ connection });

  const transfer: Transfer = {
    transferId: "tx-1001",
    fromAccount: "A-1",
    toAccount: "B-2",
    amount: 250,
  };
  const receipt = await client.workflow.execute(transferWorkflow, {
    taskQueue: "transfers",
    workflowId: `transfer-${transfer.transferId}`,
    args: [transfer],
  });
  console.log(receipt.status);
}
```

<!--
New to the TypeScript Deck. Code: snippets/src/client-anatomy.client.ts
(type-checked); the Workflow it starts is run by
snippets/tests/runtime/workflows-anatomy.test.ts.

- **Build 1 - the package.** `@temporalio/client`. No sandbox here.
- **Build 2 - the Workflow import.** Passing the function rather than the string
  `"transferWorkflow"` gets you type checking on `args` and on the result. The
  Client only reads its name; a Client in another codebase can pass the string
  instead.
- **Build 3 - the connection.** `Connection` from `@temporalio/client` is a
  different class from the Worker's `NativeConnection`. For Temporal Cloud, add
  your namespace's address, TLS and an API key here.
- **Build 4 - the Client.** Connections are expensive and Clients are cheap to
  share. One per process.
- **Build 5 - `execute`.** Starts the Workflow and waits for the receipt. The
  next section is the difference between this and `start`.
- **Build 6 - the Task Queue.** The only link between this Client and the
  Worker. A typo here and the Workflow waits forever on a queue nobody polls.
- **Build 7 - the Workflow ID.** `transfer-tx-1001`, built from the business ID,
  so a retried request cannot move the money twice, and support can find the
  Workflow by transfer number.
- **Build 8 - the input.** A tuple checked against `transferWorkflow`'s
  parameters.
- **Build 9 - the result.** `receipt` is a `Receipt`, inferred from the
  Workflow's signature.
-->
