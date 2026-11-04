---
layout: code-walkthrough
heading: Make it an Activity if it…
notes:
  - Talks to external systems — APIs, databases, message queues
  - Does I/O — file system, network calls
  - Uses non-deterministic operations. Neither belongs in a Workflow
---

::code::

```java {all|1-2|4-7|9-11}
// Talks to external systems
var project = http.send(get(API + "/projects/" + req.projectId()), ofString());

// Does I/O
var query = db.prepareStatement("SELECT * FROM reviewer_history WHERE project_id = ?");
query.setString(1, req.projectId());
query.executeQuery();

// Uses non-deterministic operations
UUID.randomUUID(); // ❌ in a Workflow
System.getenv("PRICING_TIER"); // ❌ in a Workflow
```

<!--
Source: PPT slide 30. The segment as a whole is PPT slides 29-37, merged with
the expanded edition's slides 40-54 (`{{BLOCK:activity-patterns}}` and
`{{BLOCK:workflow-patterns}}`). Slide notes cite 29-37 where the slide follows
the Foundations deck, and 40-54 where it is one of the expanded slides.
Code: snippets/src/main/java/io/temporal/devdays/essentials/patterns4/ActivityReasons.java
(compiled, not run: it needs a real API and database).

The most important distinction in Temporal, and the one people get wrong for
the first month. Workflows are the coordinators: they make decisions, handle
timing and manage state, and they must be deterministic because they replay
from Event History. Activities are the workers: they talk to databases, call
APIs, send emails. They can fail, be retried, and use any library on the
classpath.

The mental model to repeat until it sticks: if it has side effects or talks to
the outside world, it is an Activity. If it is decision logic or coordination,
it stays in the Workflow.

This slide:

- **Build 1 -** anything across a network boundary. It can be slow, it can be
  down, and it needs its own retry policy.
- **Build 2 -** the same argument for disk and database.
- **Build 3 -** the determinism rule, made concrete: a value that can differ
  between the first run and the replay.

On build 3, say what actually happens rather than "it is banned": the Java SDK
runs Workflow code on ordinary JVM threads, with no sandbox, so both lines run
without complaint. That is the trap. On replay `UUID.randomUUID()` returns a
new value, and the Worker that replays may see a different environment
variable. If the value steers a decision, the replay issues different Commands
from the history and fails the Workflow Task with a non-determinism error; if
it is only stored, the Workflow's state silently diverges. A replay has to make
the decisions it made the first time.

What people do not expect on this list, and should: the JDK's clock, random
and sleep. `System.currentTimeMillis()`, `Instant.now()`, `new Random()` and
`Thread.sleep()` are not replay-safe in a Workflow, and neither is starting
your own threads. The `Workflow` class has a replacement for each:
`Workflow.currentTimeMillis()`, `Workflow.newRandom()`,
`Workflow.randomUUID()` and `Workflow.sleep()`. They return the values
recorded for this run, so they replay identically (the runtime test checks
that a Query, which replays the Workflow, sees the same `Workflow.randomUUID()`
and a different `UUID.randomUUID()`). For configuration, pass it in as a
Workflow argument or read it in an Activity.

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

```java
// RfiActivitiesImpl.java
@Override
public boolean validateEligibility(RfiRequest req) {
  boolean rfiIdValid = req.rfiId().matches("RFI-\\d{4}");
  boolean submittedRecently =
      req.submittedAt().isAfter(Instant.now().minus(Duration.ofDays(30)));
  boolean withinCostThreshold = req.estimatedCost() < 50_000;

  return rfiIdValid && submittedRecently && withinCostThreshold;
}

// EligibilityWorkflowImpl.java
@Override
public boolean check(RfiRequest req) {
  boolean isEligible = activities.validateEligibility(req);
  return isEligible;
}
```

::default::

Complex validation logic. Fast, in-memory, no external dependency — and a clean
boundary to unit test on its own.

<!--
Source: PPT slide 42.
Code: snippets/src/main/java/io/temporal/devdays/essentials/patterns4/RfiActivitiesImpl.java
and EligibilityWorkflowImpl.java.

Note the tension with the "Keep it in the Workflow" slides later in this
segment, and name it rather than hoping nobody
notices: these three checks are pure, in-memory, and deterministic, so by the
"keep it in the Workflow" rule they could live in the Workflow. (`Instant.now()`
is fine here because this is an Activity; in the Workflow it would have to be
`Workflow.currentTimeMillis()`.)

The reason to pull them out anyway is testability. You get a named unit with a
contract you can test without a Workflow test environment: construct
`RfiActivitiesImpl` and call the method from a plain JUnit test.
-->

---
layout: code-stack
heading: Make it an Activity if it… can fail on its own
proseMax: 30
---

::code::

```java
// RfiActivitiesImpl.java
@Override
public void notifyReviewer(RfiRequest req, String reviewer) {
  emailService.send(reviewer, "RFI " + req.rfiId() + " requires your review");
}

// ReviewWorkflowImpl.java
@Override
public void review(RfiRequest req, String assignedReviewer) {
  activities.notifyReviewer(req, assignedReviewer);
}
```

::default::

Sending mail fails for reasons that have nothing to do with you. Wrapped in an
Activity, Temporal retries it until it succeeds or the policy runs out.

<!--
Source: PPT slide 43.
Code: snippets/src/main/java/io/temporal/devdays/essentials/patterns4/RfiActivitiesImpl.java
and ReviewWorkflowImpl.java. The runtime test makes the send fail twice and
checks the email still goes out exactly once.

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

```java
// RfiActivitiesImpl.java
@Override
public void generateAnnualReport(RfiRequest req) {
  // compiles all RFIs for the year into a report
  reportApi.compileAnnual(req.projectId());
}

// AnnualReportWorkflowImpl.java
private final RfiActivities activities =
    Workflow.newActivityStub(
        RfiActivities.class,
        ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofHours(2)).build());

@Override
public void run(RfiRequest req) {
  activities.generateAnnualReport(req);
}
```

::default::

Two hours of work, and the timeout that allows it is set by the **caller**. The
Activity has no idea it has two hours.

<!--
Source: PPT slide 44.
Code: snippets/src/main/java/io/temporal/devdays/essentials/patterns4/RfiActivitiesImpl.java
and AnnualReportWorkflowImpl.java. The runtime test reads the two-hour
Start-To-Close Timeout back off the `ActivityTaskScheduled` Event.

Why this cannot stay in the Workflow: Workflow code must not do the I/O in the
first place, and a long computation there would hold the Workflow thread. The
Worker's deadlock detector fails the Workflow Task when a Workflow thread does
not yield for one second (the default `WorkerOptions` deadlock detection
timeout). As an Activity it gets its own timeout, its own retries, and it can
heartbeat.

As an Activity, the Worker can restart freely. Temporal reschedules the
Activity and retries it from the top, or, if it heartbeats its progress, from
the last checkpoint it recorded.

The timeout placement is the design point, and the same one you will see on the
Workflow anatomy slide: policy lives with the caller, in the `ActivityOptions`
on the stub, not scattered through the implementations.

Heartbeating is the companion concept. For a two-hour Activity you want
`Activity.getExecutionContext().heartbeat(...)` calls inside the method body so
Temporal learns it has died in well under two hours. That gets its own segment
— forward-reference it, do not teach it here.
-->

---
layout: subsection
eyebrow: DESIGN PATTERNS
---

# Keep it in the Workflow if it…

<!--
Source: PPT slide 31, expanded into slides 45-49 of the expanded edition,
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

```java
@Override
public ApprovalStatus triage(Claim claim) {
  if (claim.amount() > 10_000 || claim.type() == ClaimType.SURGERY) {
    return ApprovalStatus.REQUIRES_REVIEW;
  }

  // continue to Activities...
  return activities.autoApprove(claim);
}
```

::default::

The decision itself is the orchestration. It is deterministic, it is cheap, and
it belongs where you can read it.

<!--
Source: PPT slide 32.
Code: snippets/src/main/java/io/temporal/devdays/essentials/patterns4/TriageWorkflowImpl.java.
The runtime test checks a surgery claim is sent for review with no Activity
scheduled at all.

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

```java
@Override
public Decision adjudicate(Applicant applicant) {
  CreditCheck creditCheck = activities.runCreditCheck(applicant);
  FraudCheck fraud = activities.checkFraud(applicant, creditCheck);
  Decision decision = activities.makeDecision(applicant, creditCheck, fraud);

  return decision;
}
```

::default::

Chaining Activities with results flowing between the steps. This is the
Workflow's actual job.

<!--
Source: PPT slide 46.
Code: snippets/src/main/java/io/temporal/devdays/essentials/patterns4/LoanWorkflowImpl.java.

Point at how ordinary it looks. Three method calls and three local variables.
No queues, no callbacks, no futures, no state machine, no correlation IDs.

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

```java
// Sleep for 30 days — durable, costs nothing while waiting
Workflow.sleep(Duration.ofDays(30));

// Wait for a customer response — survives restarts
Workflow.await(() -> response != null);
```

::default::

Scheduling future actions and reacting to external events. Both are durable:
they survive Worker restarts and deploys.

<!--
Source: PPT slide 47.
Code: snippets/src/main/java/io/temporal/devdays/essentials/patterns4/FollowUpWorkflowImpl.java,
where `response` is a field a Signal sets. The runtime test skips 31 days of
test-server time and reads the 30-day Timer back out of the history.

The thirty-day sleep is the slide that changes how people think, so give it a
beat. No cron, no scheduler table, no polling job, no "wake up and check"
Lambda. One line, and the process can be redeployed forty times in between.

The mechanism, if asked: Temporal records a Timer in history and the Workflow is
evicted from memory. It is not a thread parked for a month. That is also why it
is `Workflow.sleep` and never `Thread.sleep`: only the first is a Timer.

`Workflow.await` is the primitive the Signals segment builds on. Timing and
events are the same mechanism from the Workflow's point of view — both are
"stop here until something happens."
-->

---
layout: code-stack
heading: Keep it in the Workflow if it… is a pure calculation
proseMax: 30
---

::code::

```java
boolean isValidEmail = email.matches("[^@\\s]+@company\\.com");

double riskScore = claim.amount() / 1000 + claim.history().size();
```

::default::

Deterministic in, deterministic out. Same inputs, same answer, every replay.

<!--
Source: PPT slide 48.
Code: snippets/src/main/java/io/temporal/devdays/essentials/patterns4/RiskWorkflowImpl.java.

These call nothing external, are completely idempotent, and produce the same
output on every replay. Round-tripping them through an Activity buys you a
Temporal Server call and three history Events in exchange for nothing.

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

```java
ActivityOptions options =
    ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build();
ClaimActivities activities = Workflow.newActivityStub(ClaimActivities.class, options);

Approval approval = activities.getApproval(claim);
```

::default::

The Activity call is the **checkpoint**. Its result is written to history, so a
Worker restart replays up to this line and carries on from there.

<!--
Source: PPT slide 49.
Code: snippets/src/main/java/io/temporal/devdays/essentials/patterns4/ClaimWorkflowImpl.java.
The runtime test finds the `ActivityTaskCompleted` Event in the history.

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

- Making an Activity just to log something → use `Workflow.getLogger(...)`
- Making an Activity for a simple calculation → keep it in the Workflow
- Putting a business decision in an Activity → it belongs in the Workflow
- One Activity doing two things → split it, so each part is safe to retry

<!--
Source: PPT slide 33 (the expanded edition's slides 50 and 53, which were
duplicates of each other), merged with the headlines from slides 35 and 36 so
the list is in one place. The next two slides give those two their code.

The first two come from treating "Activity" as "method." Every Activity call
is at least three Events in history (scheduled, started, completed) and a round
trip to the Server. For a log line, that is absurd.

Logging is the one worth a sentence: `Workflow.getLogger(MyWorkflowImpl.class)`
returns an SLF4J logger that is replay-aware. It stays quiet while the Workflow
is replaying (unless `WorkerFactoryOptions.setEnableLoggingInReplay(true)`),
which is exactly what you want and exactly what `System.out.println` or a plain
SLF4J logger will not do: they log the same line again on every replay.
-->

---
layout: code-stack
heading: A business decision hidden in an Activity
proseMax: 26
---

::code::

```java
// ❌ Don't do this — the rule is invisible in the Workflow and in history
String decision = activities.evaluateClaim(claim, coverage);

// ✅ Do this — the rule is in the Workflow, where you can read it
String decision;
if (claim.amount() > coverage.maxBenefit()) {
  decision = "partial_approval";
} else if (claim.isEmergency()) {
  decision = "fast_track";
} else {
  decision = "standard_review";
}
```

::default::

The branch is the business logic. Decisions belong in the Workflow.

<!--
Source: PPT slide 35.
Illustrative: the two halves are alternatives, not one method (both declare
`decision`), so this block is parsed but not compiled or run.

A common justification for the ❌ version is that `evaluateClaim` might return
something different on replay. It will not: a completed Activity's result is
read from history on replay. The real cost is elsewhere: the decision is now
hidden behind a network call, retried as if it were I/O, and invisible when you
read the Workflow.

Ask the room which version they would rather read in six months during an
incident. That usually settles it faster than the determinism argument.

The second cost, beyond readability: in the ❌ version the Workflow history
records "EvaluateClaim completed" with an opaque result. In the ✅ version the
inputs are in history and the branch is in the code, so the decision is fully
reconstructable.
-->

---
layout: code-stack
heading: When to split an Activity
proseMax: 34
---

::code::

```java
// ❌ One Activity doing two things — not safe to retry
@Override
public User createUser(UserData data) {
  User user = db.addUser(data);
  emailService.sendWelcomeEmail(user.email());

  return user;
}
```

::default::

Two different services in one Activity. If the email fails, the whole Activity
retries — and you create the user twice.

<!--
Source: PPT slide 36.
Code: snippets/src/main/java/io/temporal/devdays/essentials/patterns4/UserActivitiesImpl.java.
The runtime test fails the email once and finds two rows in the users table,
and the returned user is the second one, id 2.

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
