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
- [TypeScript sample: Saga with array-based compensations](https://github.com/temporalio/samples-typescript/tree/main/saga)

</div>

<style>
.dense :is(p, li) { font-size: 1.2rem; line-height: 1.45; }
</style>

<!--
Source: Temporal docs, Saga pattern page (Related). Ported from the Python Deck.

Thirty seconds. "Related" from the docs page, with only the TypeScript sample.
Note the sample registers each compensation after its step; this section
registered before, and the "When to register" slide says when after is fine.

Child Workflows and Heartbeats, covered earlier in this Deck, both extend what
the room just built: a Saga step can be a Child Workflow, and a long step
should heartbeat.
-->
