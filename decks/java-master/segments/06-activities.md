---
layout: section
---

<ZiggyHero scene="work">

# Java Activities

The work. Ordinary Java that is allowed to fail.

</ZiggyHero>

<!--
Source: PPT slides 51-61.

The relief slide. After the Workflow rules, Activities have almost none.

The original had an interface slide and an implementation slide here too, and
so does this: an Activity is an `@ActivityInterface` the Workflow's stub is
typed from, and a class the Worker runs.
-->

---
layout: default
---

# What is an Activity?

The unit of work that talks to the outside world. Anything that can fail on its
own, and should retry on its own.

- **A plain Java method.** On an interface, implemented by an ordinary class.
- **Outside the Workflow's rules.** Full I/O, any library, any clock.
- **Retried by the platform.** It throws; Temporal decides what happens next.
- **Recorded.** Its result goes into Event History; replay reads it back.
- Keep it a thin wrapper

<!--
Source: PPT slide 51.

The framing slide before the anatomy. Two minutes at most. The code on the next
two slides does the real teaching, so resist explaining it twice.

The bullets are deliberately about what an Activity *is*, not about the shape
of the file. The anatomy walkthroughs that follow take the shape apart one
line at a time, and these would steal their beats.

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
heading: Java Activity interface
notes:
  - "<code>@ActivityInterface</code> marks the contract. The Workflow's stub is typed from it, so a typo is a compile error"
  - "Every method is an Activity. The Activity Type is the method name, capitalised: <code class=\"ref\">SubmitOrder</code>"
  - Arguments and return values must be <strong>serializable</strong>. Jackson turns them into JSON and back
---

::code::

```java {all|2-3|4|4}
// OrderActivities.java
@ActivityInterface
public interface OrderActivities {
  OrderResult submitOrder(Order order);
}
```

<!--
Source: PPT slides 52-61, one build per slide in the original.
Code: snippets/src/main/java/io/temporal/devdays/essentials/activities/OrderActivities.java,
run against a real local HTTP server by
snippets/src/test/java/io/temporal/devdays/essentials/activities/OrderActivitiesTest.java.

- **Build 1 - the interface.** `@ActivityInterface` is what the Workflow
  sees: `Workflow.newActivityStub(OrderActivities.class, options)` returns a
  proxy that implements it. Because the stub is typed from this interface, a
  misspelt Activity or a wrong argument in the Workflow is a compile error, not
  a runtime surprise.
- **Build 2 - the name.** Every method on the interface is an Activity; you
  only need `@ActivityMethod` to rename one. The Activity Type defaults to the
  method name with its first letter capitalised, so `submitOrder` is
  registered as `SubmitOrder`. That string is what appears in the Event
  History and the Web UI.
- **Build 3 - the signature.** Builds 2 and 3 point at the same line on
  purpose. `Order` arrives in the Activity as JSON that Jackson turns back into
  a record. Pass data, never a client, a connection, or a lambda.
-->

---
layout: code-walkthrough
heading: Java Activity implementation
notes:
  - "A plain class that <code>implements</code> the interface. The Worker registers an <strong>instance</strong> of it"
  - So it can hold long-lived clients and config, built once and shared by every call
  - Finally, non-deterministic code! Full I/O, any library, any external call
  - Throwing triggers Temporal's retry logic. No retry code of your own
  - "Checked exceptions go through <code>Activity.wrap</code>, which rethrows them unchecked. Still retried"
---

::code::

```java {all|2|3-9|14-18|19-21|23-24}
// OrderActivitiesImpl.java
public class OrderActivitiesImpl implements OrderActivities {
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private final URI ordersApi;

  public OrderActivitiesImpl(URI ordersApi) {
    this.ordersApi = ordersApi;
  }

  @Override
  public OrderResult submitOrder(Order order) {
    try {
      HttpRequest request = HttpRequest.newBuilder(ordersApi.resolve("/submit"))
          .header("Content-Type", "application/json")
          .POST(BodyPublishers.ofString(json.writeValueAsString(order)))
          .build();
      HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException("Orders API returned " + response.statusCode());
      }
      return json.readValue(response.body(), OrderResult.class);
    } catch (IOException | InterruptedException e) {
      throw Activity.wrap(e);
    }
  }
}
```

<!--
Source: PPT slides 52-61, continued.
Code: snippets/src/main/java/io/temporal/devdays/essentials/activities/OrderActivitiesImpl.java,
run against a real local HTTP server by
snippets/src/test/java/io/temporal/devdays/essentials/activities/OrderActivitiesTest.java,
which also answers 503 once and checks the second attempt succeeds.

- **Build 1 - the class.** No base class. It becomes an Activity when a Worker
  is given an instance: `worker.registerActivitiesImplementations(new
  OrderActivitiesImpl(ordersApi))`.
- **Build 2 - an instance, not a class.** Unlike a Workflow, which the Worker
  instantiates per Execution, one Activity object serves every call on that
  Worker. That is where your HTTP client, connection pool and configuration
  go, built once. It also means it must be thread-safe: many Activities run on
  it at once.
- **Build 3 - just code.** You are outside the Workflow's determinism rules.
  This is ordinary Java: `HttpClient`, a JDBC driver, the filesystem,
  `Instant.now()`, `new Random()`, all fine.
- **Build 4 - just throw.** A non-200 response throws, and that is the entire
  error handling. The Activity has no attempt counter, no backoff, no retry
  loop. It throws, and the platform retries according to the policy the
  Workflow set. Every error is retried by default, so a permanent failure such
  as a 400 should throw `ApplicationFailure.newNonRetryableFailure(...)` or be
  listed in the Retry Policy's `setDoNotRetry(...)`. The error-handling
  segment shows how.
- **Build 5 - checked exceptions.** The interface method declares none, so
  `IOException` and `InterruptedException` cannot escape as they are.
  `Activity.wrap(e)` rethrows them as unchecked; Temporal still sees the
  failure, records the original type, and retries.

Two things to say before moving on:

- Assume every Activity runs more than once. A timeout can fire on an attempt
  that actually succeeded. Make them idempotent.
- Activities run on the Worker's Activity thread pool, alongside every other
  Activity on that Worker. A blocking call is fine here, unlike in a Workflow,
  but it holds one of the Worker's Activity slots until it returns, so give
  long calls a timeout.

The sentence to land: the interface is the contract the Workflow compiles
against, and a Worker that registers an instance is what makes it run. Nothing
else in the class is Temporal.
-->
