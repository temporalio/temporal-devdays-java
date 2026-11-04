---
layout: section
---

<ZiggyHero scene="work">

# TypeScript Activities

The work. Ordinary async code that is allowed to fail.

</ZiggyHero>

<!--
Source: PPT slides 51-61.

The relief slide. After the Workflow rules, Activities have almost none.

The original had an interface slide and an implementation slide here too. In
TypeScript an Activity is an exported function, so it is one walkthrough.
-->

---
layout: default
---

# What is an Activity?

The unit of work that talks to the outside world. Anything that can fail on its
own, and should retry on its own.

- **A plain async function.** No decorator, no base class.
- **Outside the Workflow's rules.** Full I/O, any library, any clock.
- **Retried by the platform.** It throws; Temporal decides what happens next.
- **Recorded.** Its result goes into Event History; replay reads it back.
- Keep it a thin wrapper

<!--
Source: PPT slide 51.

The framing slide before the anatomy. Two minutes at most. The code on the next
slide does the real teaching, so resist explaining it twice.

The bullets are deliberately about what an Activity *is*, not about the shape
of the file. The anatomy walkthrough that follows takes the shape apart one
line at a time, and these would steal its beats.

The recorded bullet is the one worth dwelling on, because it is the one that is
not obvious: the recorded result is what makes replay cheap and what makes a
crashed Workflow recoverable. An Activity that already completed does not run
again.

"Thin wrapper": the Activity calls the API or the database and returns. The
decisions (what next, what if it failed) stay in the Workflow, where they are
durable.

Good question to take here if the room is quiet: "so what stops me putting
everything in Activities?" Answer: nothing, and it would work. You would just
have no orchestration left, and a Workflow that is one Activity call is a
Workflow that cannot make a decision. That is the design-patterns segment, coming up.
-->

---
layout: code-walkthrough
heading: TypeScript Activity anatomy
notes:
  - "No decorator. <code>export</code> from a module the Worker registers is the whole story"
  - The function name is the Activity Type. It is the name the Workflow destructures from <code class="ref">proxyActivities</code>
  - Arguments and return values must be <strong>serializable</strong>. The types are checked at compile time only
  - Finally, non-deterministic code! Full I/O, any library, any external call
  - Throwing triggers Temporal's retry logic. No retry code of your own
---

::code::

```ts {all|4|4|4|5-13|10-12}
// activities.ts
import type { Order, OrderResult } from "./models";

export async function submitOrder(order: Order): Promise<OrderResult> {
  const response = await fetch(`${ORDERS_API}/submit`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(order),
  });
  if (!response.ok) {
    throw new Error(`Orders API returned ${response.status}`);
  }
  return (await response.json()) as OrderResult;
}
```

<!--
Source: PPT slides 52-61.
Code: snippets/src/activities-anatomy.activities.ts, run (with `fetch` stubbed)
by snippets/tests/runtime/workflows-anatomy.test.ts.

- **Build 1 - no decorator.** Good contrast moment if the room knows another
  SDK: Python has `@activity.defn`, Java has `@ActivityMethod`. TypeScript has
  nothing. The function becomes an Activity when a Worker is given it.
- **Build 2 - the name.** The Activity Type defaults to the function name, or
  more precisely to the key it is registered under on the Worker. Because the
  Workflow's stub is typed from this module with `typeof activities`, a typo in
  the Workflow is a compile error, not a runtime surprise.
- **Build 3 - the signature.** Builds 1 to 3 point at the same line on purpose.
  The types are not checked at runtime: the SDK turns the JSON back into a plain
  object, not an instance of anything. Pass data, never a client, a class
  instance, or a function.
- **Build 4 - just code.** You are outside the Workflow isolate. This is
  ordinary Node: `fetch`, a database driver, the filesystem, `Date.now()`,
  `Math.random()`, all fine.
- **Build 5 - just throw.** A non-2xx response throws, and that is the entire
  error handling. The Activity has no attempt counter, no backoff, no `catch`.
  It throws, and the platform retries according to the policy the Workflow set.
  Every error is retried by default, so a permanent failure such as a 400 should
  throw `ApplicationFailure.nonRetryable(...)` or be listed in the Retry
  Policy's `nonRetryableErrorTypes`. The error-handling segment shows how.

Two things to say before moving on:

- Assume every Activity runs more than once. A timeout can fire on an attempt
  that actually succeeded. Make them idempotent.
- Activities run on the Worker's Node event loop, alongside every other
  Activity on that Worker. Never block it with synchronous CPU work or a
  synchronous library call; that stalls the whole Worker, Heartbeats included.

The sentence to land: the only thing that makes this an Activity is a Worker
that registers it. There is nothing in the file itself.
-->
