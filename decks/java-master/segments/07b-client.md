---
layout: section
---

<ZiggyHero scene="bridge">

# Java Clients

How your application talks to Temporal.

</ZiggyHero>

<!--
The Workers section showed the process that runs the code; this one shows the
code that asks for it to run.
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
The picture to leave in the room: the Client and the Worker never meet. The
Client asks the Service to start a Workflow; the Service puts a Task on the
Task Queue; a Worker polling that queue picks it up. Either side can be down
while the other carries on, which is why a Client can start a Workflow even if
no Worker is running yet. It simply waits on the queue.

The Client is `io.temporal.client.WorkflowClient`, in the same `temporal-sdk`
artifact as the Worker. It is plain Java, so it goes wherever your application
already runs: a Spring controller, a batch job, a CLI.
-->

---
layout: code-walkthrough
heading: Java Client
notes:
  - "Connects to the Temporal Service: the same <code>WorkflowServiceStubs</code> the Worker used"
  - The Client. Thread-safe; create one per process and share it
  - "A typed stub built from the Workflow <strong>interface</strong>: its name and types. The Client never runs the implementation"
  - Must match the Task Queue the Worker polls
  - "The business ID is the Workflow ID. While it runs, a second start with it is rejected"
  - The input, type-checked against the Workflow method's parameter
  - Calling the Workflow method starts the Workflow and waits for its result
  - The receipt, typed from the Workflow method's return value
---

::code::

```java {all|4-5|6|12-13|15|16|11|19|20}
// TransferClient.java
public class TransferClient {
  public static void main(String[] args) {
    WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(
        WorkflowServiceStubsOptions.newBuilder().setTarget("localhost:7233").build());
    WorkflowClient client = WorkflowClient.newInstance(service);
    run(client);
  }

  static void run(WorkflowClient client) {
    Transfer transfer = new Transfer("tx-1001", "A-1", "B-2", 250);
    TransferWorkflow workflow = client.newWorkflowStub(
        TransferWorkflow.class,
        WorkflowOptions.newBuilder()
            .setTaskQueue("transfers")
            .setWorkflowId("transfer-" + transfer.transferId())
            .build());

    Receipt receipt = workflow.transfer(transfer);
    System.out.println(receipt.status());
  }
}
```

<!--
Code: snippets/src/main/java/io/temporal/devdays/essentials/client/TransferClient.java,
run by TransferClientTest (`run(client)` against the test server; `main` is
compiled, never run).

- **Build 1 - the connection.** `WorkflowServiceStubs`, exactly as in the
  Worker. For Temporal Cloud, set your namespace's address, TLS and an API key
  on the stubs options, and the namespace on `WorkflowClientOptions`.
- **Build 2 - the Client.** Connections are expensive and Clients are cheap to
  share. One per process; in Spring, one bean.
- **Build 3 - the stub.** `newWorkflowStub` takes the `@WorkflowInterface`, not
  the implementation class. The interface gives the Workflow Type
  (`TransferWorkflow`) and the compile-time types; the implementation never
  needs to be on the Client's classpath. A Client in another codebase can use
  `newUntypedWorkflowStub("TransferWorkflow", options)` instead.
- **Build 4 - the Task Queue.** The only link between this Client and the
  Worker. A typo here and the Workflow waits forever on a queue nobody polls.
- **Build 5 - the Workflow ID.** `transfer-tx-1001`, built from the business ID,
  so a retried request cannot start a second transfer while the first is still
  running (set `WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE` to also refuse one
  after it closes), and support can find the Workflow by transfer number.
- **Build 6 - the input.** A record, checked by the compiler against
  `transfer(Transfer)`. It travels as JSON, so keep it serializable.
- **Build 7 - the call.** Calling the `@WorkflowMethod` on the stub starts the
  Workflow and blocks this thread until it completes. The next section is the
  difference between this and `WorkflowClient.start`.
- **Build 8 - the result.** `receipt` is a `Receipt`, the Workflow method's
  return type.
-->
