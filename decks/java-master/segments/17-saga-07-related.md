---
layout: default
---

# Related

<div class="dense">

**Patterns**

- **Error Handling & Retry Patterns**: often combined with the Saga pattern to handle
  transient failures before compensating
- **Child Workflows**: organize complex Sagas with multiple sub-Sagas
- **Long-Running Activity**: Heartbeats work well with long-running compensation Activities
- **Early Return**: return initialization results before compensation runs

**Guides and samples**

- [Recover business processes without restarting](https://docs.temporal.io/guides/recover-without-restart)
- [Saga pattern in the Temporal docs](https://docs.temporal.io/design-patterns/saga-pattern)
- [Java sample: trip booking Saga with `io.temporal.workflow.Saga`](https://github.com/temporalio/samples-java/tree/main/core/src/main/java/io/temporal/samples/bookingsaga)
- [Java SDK reference: `io.temporal.workflow.Saga`](https://javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/workflow/Saga.html)

</div>

<style>
.dense :is(p, li) { font-size: 1.2rem; line-height: 1.45; }
</style>

<!--
Source: Temporal docs, Saga pattern page (Related).

Thirty seconds. "Related" from the docs page, with the Java sample and the
`Saga` class reference. The sample has the same shape as this section: it
registers each compensation before its step, catches `ActivityFailure`, and
re-throws. It also wraps `saga.compensate()` in
`Workflow.newDetachedCancellationScope`, so the undos still run when the
Workflow is cancelled, which the lab code does not handle.

Child Workflows and Heartbeats, covered earlier in this Deck, both extend what
the room just built: a Saga step can be a Child Workflow, and a long step
should heartbeat.
-->
