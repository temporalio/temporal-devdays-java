---
layout: code-walkthrough
heading: Make it an Activity if it…
notes:
  - Talks to external systems — APIs, databases, message queues
  - Does I/O — file system, network calls
  - Uses non-deterministic operations. Neither works inside a Workflow
---

::code::

```ts {all|1-2|4-7|9-11}
// Talks to external systems
const project = await fetch(`https://<api-endpoint>/projects/${req.projectId}`);

// Does I/O
await db.query("SELECT * FROM reviewer_history WHERE project_id = $1", [
  req.projectId,
]);

// Uses non-deterministic operations
crypto.randomUUID(); // ❌ in a Workflow
process.env.PRICING_TIER; // ❌ in a Workflow
```

<!--
Source: PPT slide 30. The segment as a whole is PPT slides 29-37, merged with
the TypeScript edition's slides 40-54 (`{{BLOCK:activity-patterns}}` and
`{{BLOCK:workflow-patterns}}`). Slide notes
cite 29-37 where the slide follows the Foundations deck, and 40-54 where it is
one of the TypeScript edition's expanded slides.

The most important distinction in Temporal, and the one people get wrong for
the first month. Workflows are the coordinators: they make decisions, handle
timing and manage state, and they must be deterministic because they replay
from Event History. Activities are the workers: they talk to databases, call
APIs, send emails. They can fail, be retried, and use any npm package you like.

The mental model to repeat until it sticks: if it has side effects or talks to
the outside world, it is an Activity. If it is decision logic or coordination,
it stays in the Workflow.

This slide:

- **Build 1 -** anything across a network boundary. It can be slow, it can be
  down, and it needs its own retry policy.
- **Build 2 -** the same argument for disk and database.
- **Build 3 -** the determinism rule, made concrete: a value that can differ
  between the first run and the replay.

On build 3, say what actually happens rather than "it is banned": the Workflow
runs in an isolated V8 context with no `crypto` and no `process`, so both lines
throw a `ReferenceError` (`crypto is not defined`) and fail the Workflow Task. The rule is not arbitrary: a
replay has to make the decisions it made the first time, and a fresh UUID or a
Worker's environment variable could differ on every replay.

What people expect to be on this list and is not: `Date.now()` and
`Math.random()`. Inside a Workflow the SDK makes both deterministic, giving the
Workflow Task's time and a random sequence seeded per run, so they replay
identically. For a UUID, use `uuid4()` from `@temporalio/workflow`. For
configuration, pass it in as a Workflow argument or read it in an Activity.

The original also listed two more reasons, and they get a slide each next:
complex validation you want to test on its own, and work that can fail or run
long by itself.
-->

---
layout: code-stack
heading: Make it an Activity if it… is worth testing in isolation
proseMax: 30
---

::code::

```ts
// activities.ts
export async function validateEligibility(req: RFIRequest): Promise<boolean> {
  const rfiIdValid = /^RFI-\d{4}$/.test(req.rfiId);
  const submittedRecently =
    new Date(req.submittedAt) > new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
  const withinCostThreshold = req.estimatedCost < 50_000;

  return rfiIdValid && submittedRecently && withinCostThreshold;
}

// workflow.ts
const isEligible = await validateEligibility(req);
```

::default::

Complex validation logic. Fast, in-memory, no external dependency — and a clean
boundary to unit test on its own.

<!--
Source: PPT slide 42.

Note the tension with the "Keep it in the Workflow" slides later in this
segment, and name it rather than hoping nobody
notices: these three checks are pure, in-memory, and deterministic, so by the
"keep it in the Workflow" rule they could live in the Workflow.

The reason to pull them out anyway is testability. You get a named unit with a
contract you can test without a Workflow test environment.
-->

---
layout: code-stack
heading: Make it an Activity if it… can fail on its own
proseMax: 30
---

::code::

```ts
// activities.ts
export async function notifyReviewer(
  req: RFIRequest,
  reviewer: string,
): Promise<void> {
  await emailService.send(reviewer, `RFI ${req.rfiId} requires your review`);
}

// workflow.ts
await notifyReviewer(req, assignedReviewer);
```

::default::

Sending mail fails for reasons that have nothing to do with you. Wrapped in an
Activity, Temporal retries it until it succeeds or the policy runs out.

<!--
Source: PPT slide 43.

`emailService.send` fails for any number of external reasons: SMTP timeout,
rate limiting, a network blip, someone's DNS.

The cost of getting this wrong: called directly from Workflow code, a transient
email failure takes down the entire Workflow execution. Wrapped in an Activity,
it is a retry nobody hears about.

Say the line again, because it is the whole segment: the Activity knows nothing
about retries. It throws, and Temporal handles the rest.
-->

---
layout: code-stack
heading: Make it an Activity if it… takes significant time
proseMax: 34
---

::code::

```ts
// activities.ts
export async function generateAnnualReport(req: RFIRequest): Promise<void> {
  // compiles all RFIs for the year into a report
  const response = await fetch(
    `https://<api-endpoint>/rfi/${req.projectId}/annual`,
  );
  await response.arrayBuffer();
}

// workflow.ts
const { generateAnnualReport } = proxyActivities<typeof activities>({
  startToCloseTimeout: "2 hours",
});

await generateAnnualReport(req);
```

::default::

Two hours of work, and the timeout that allows it is set by the **caller**. The
Activity has no idea it has two hours.

<!--
Source: PPT slide 44.

Why this cannot stay in the Workflow: Workflow code cannot do the I/O in the
first place, and a long computation there would trip the Worker's 5-second
limit on Workflow code that does not yield, and fail the Workflow Task. As an Activity it gets its own timeout, its own retries,
and it can heartbeat.

As an Activity, the Worker can restart freely. Temporal reschedules the
Activity and retries it from the top, or, if it heartbeats its progress, from
the last checkpoint it recorded.

The timeout placement is the design point, and the same one you saw on the
Workflow anatomy slide: policy lives with the caller, in one place, not
scattered through the implementations.

Heartbeating is the companion concept. For a two-hour Activity you want
`heartbeat()` calls inside the function body so Temporal learns it has died in
well under two hours. That gets its own segment — forward-reference it, do not
teach it here.
-->

---
layout: subsection
eyebrow: DESIGN PATTERNS
---

# Keep it in the Workflow if it…

<!--
Source: PPT slide 31, expanded into the TypeScript edition's slides 45-49,
`{{BLOCK:workflow-patterns}}`.

The other half. Five criteria, one slide each, all short:

- Makes business logic decisions
- Coordinates between Activities
- Manages timing and state transitions
- Is a pure calculation
- Needs to survive process restarts

The last one is the one people underrate. A local variable in a Workflow
survives a Worker crash, because it is rebuilt by replay. A local variable in an
Activity does not.
-->

---
layout: code-stack
heading: Keep it in the Workflow if it… makes business decisions
proseMax: 26
---

::code::

```ts
export async function adjudicationWorkflow(
  claim: Claim,
): Promise<ApprovalStatus> {
  if (claim.amount > 10_000 || claim.type === "Surgery") {
    return ApprovalStatus.RequiresReview;
  }

  // continue to activities...
}
```

::default::

The decision itself is the orchestration. It is deterministic, it is cheap, and
it belongs where you can read it.

<!--
Source: PPT slide 32.

This is what "orchestration" means in practice: an `if`. There is nothing to
retry and nothing to time out, so wrapping it in an Activity buys nothing and
costs a round trip to the Server.

The practical argument, which lands better than the theoretical one: this
branch is the business rule. Put it in an Activity and it is invisible in the
Workflow history — you see "Activity completed" and not *why*. Keep it in the
Workflow and the decision is in the code and in the Event History.
-->

---
layout: code-stack
heading: Keep it in the Workflow if it… coordinates Activities
proseMax: 26
---

::code::

```ts
export async function adjudicationWorkflow(
  applicant: Applicant,
): Promise<Decision> {
  const creditCheck = await runCreditCheck(applicant);
  const fraud = await checkFraud(applicant, creditCheck);
  const decision = await makeDecision(applicant, creditCheck, fraud);

  return decision;
}
```

::default::

Chaining Activities with results flowing between the steps. This is the
Workflow's actual job.

<!--
Source: PPT slide 46.

Point at how ordinary it looks. Three awaits and two local variables. No
queues, no callbacks, no state machine, no correlation IDs.

Then point at what is not written: every one of those three calls is
independently retried, independently timed out, and survives the process dying
between any two of them.
-->

---
layout: code-stack
heading: Keep it in the Workflow if it… manages timing
proseMax: 30
---

::code::

```ts
import { sleep, condition } from "@temporalio/workflow";

// Sleep for 30 days — durable, costs nothing while waiting
await sleep("30 days");

// Wait for a customer response — survives restarts
await condition(() => response !== null);
```

::default::

Scheduling future actions and reacting to external events. Both are durable:
they survive Worker restarts and deploys.

<!--
Source: PPT slide 47.

The thirty-day sleep is the slide that changes how people think, so give it a
beat. No cron, no scheduler table, no polling job, no "wake up and check"
Lambda. One line, and the process can be redeployed forty times in between.

The mechanism, if asked: Temporal records a timer in history and the Workflow is
evicted from memory. It is not a thread parked for a month.

`condition` is the primitive the Signals segment builds on. Timing and events
are the same mechanism from the Workflow's point of view — both are "stop here
until something happens."
-->

---
layout: code-stack
heading: Keep it in the Workflow if it… is a pure calculation
proseMax: 30
---

::code::

```ts
const isValidEmail = /^[^@\s]+@company\.com$/.test(email);

const riskScore = claim.amount / 1000 + claim.history.length;
```

::default::

Deterministic in, deterministic out. Same inputs, same answer, every replay.

<!--
Source: PPT slide 48.

These call nothing external, are completely idempotent, and produce the same
output on every replay. Round-tripping them through an Activity buys you a
Temporal Server call and two history events in exchange for nothing.

The honest caveat, because someone will push back with the previous
`validateEligibility` slide: the line between "pure calculation, keep it" and
"complex validation, extract it" is testability, not purity. Both are correct.
Extract when the logic is worth a test of its own.
-->

---
layout: code-stack
heading: Keep it in the Workflow if it… must survive restarts
proseMax: 38
---

::code::

```ts
const { getApproval } = proxyActivities<ClaimActivities>({
  startToCloseTimeout: "30 seconds",
});

const approval = await getApproval(claim);
```

::default::

The Activity call is the **checkpoint**. Its result is written to history, so a
Worker restart replays up to this line and carries on from there.

<!--
Source: PPT slide 49.

This is the slide that explains what "durable" actually means mechanically, so
do not rush it.

Everything before this line is replayed from history, not re-executed against
the outside world. Everything after it has not happened yet. The Activity
result is the boundary between the two.

The consequence worth stating: Workflow state persists across failures and
restarts without you writing a single line of persistence. No database, no
checkpoint table, no serialisation of your own.
-->

---
layout: default
---

# Common mistakes

- Making an Activity just to log something → use `log` from
  `@temporalio/workflow`
- Making an Activity for a simple calculation → keep it in the Workflow
- Putting a business decision in an Activity → it belongs in the Workflow
- One Activity doing two things → split it, so each part is safe to retry

<!--
Source: PPT slide 33 (the TypeScript edition's slides 50 and 53, which were
duplicates of each other), merged with the headlines from slides 35 and 36 so
the list is in one place. The next two slides give those two their code.

The first two come from treating "Activity" as "function." Every Activity call
is at least three Events in history (scheduled, started, completed) and a round
trip to the Server. For a log line, that is absurd.

Logging is the one worth a sentence: `log` from `@temporalio/workflow` is
replay-aware and stays quiet while the Workflow is replaying, which is exactly
what you want and exactly what a plain `console.log` will not do.
-->

---
layout: code-stack
heading: A business decision hidden in an Activity
proseMax: 26
---

::code::

```ts
// ❌ Don't do this — the rule is invisible in the Workflow and in history
const decision = await evaluateClaim(claim, coverage);

// ✅ Do this — the rule is in the Workflow, where you can read it
let decision: string;
if (claim.amount > coverage.maxBenefit) {
  decision = "partial_approval";
} else if (claim.isEmergency) {
  decision = "fast_track";
} else {
  decision = "standard_review";
}
```

::default::

The branch is the business logic. Decisions belong in the Workflow.

<!--
Source: PPT slide 35.

A common justification for the ❌ version is that `evaluateClaim` might return
something different on replay. It will not: a completed Activity's result is
read from history on replay. The real cost is elsewhere: the decision is now
hidden behind a network call, retried as if it were I/O, and invisible when you
read the Workflow.

Ask the room which version they would rather read in six months during an
incident. That usually settles it faster than the determinism argument.

The second cost, beyond readability: in the ❌ version the Workflow history
records "evaluateClaim completed" with an opaque result. In the ✅ version the
inputs are in history and the branch is in the code, so the decision is fully
reconstructable.
-->

---
layout: code-stack
heading: When to split an Activity
proseMax: 34
---

::code::

```ts
// ❌ One Activity doing two things — not safe to retry
export async function createUser(data: UserData): Promise<User> {
  const user = await db.users.add(data);
  await emailService.sendWelcomeEmail(user.email);

  return user;
}
```

::default::

Two different services in one Activity. If the email fails, the whole Activity
retries — and you create the user twice.

<!--
Source: PPT slide 36.

Think in failure modes rather than in tidiness. The question is never "are these
two things related?" It is "if the second one fails, is re-running the first one
safe?"

Here it is not. The insert succeeded, the email did not, and the retry inserts
again.

Two ways out, and it is worth naming both: split into two Activities so each
retries independently, or make the single Activity idempotent with a key the
database can deduplicate on. Splitting is the one to reach for first, because
the Workflow then records that the user was created and never re-runs it.
-->
