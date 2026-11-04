---
layout: section
---

<ZiggyHero scene="demo">

# Demo

</ZiggyHero>

<!--
Source: PPT slide 20.

Run a Workflow live if you have one ready: start a Worker, start a few
Workflows, and open the Web UI at localhost:8233. Kill the Worker mid-run and
start it again. The Workflow carries on from where it stopped. That is the
whole pitch in thirty seconds, and it lands harder than any slide.

If you have nothing prepared, any of today's Workshops will do, because each
runs its Worker and its starter as separate processes. In the Exercise
directory, add a `Workflow.sleep(Duration.ofSeconds(30))` to the Workflow,
run `mvn -q compile exec:java@worker` in one terminal and
`mvn -q compile exec:java@starter` in another, kill the Worker (Ctrl-C) during
the sleep, start it again, and watch the Workflow finish.
-->

---
layout: default
---

# Local development

```bash
brew install temporal
temporal server start-dev

git clone https://github.com/temporalio/samples-java && cd samples-java
./gradlew -q execute -PmainClass=io.temporal.samples.hello.HelloActivity
```

- **MIT-licensed open source.** Same server here, on-prem, or in Temporal Cloud
- **One binary**, with the Web UI at `localhost:8233`
- **Self-hosted state** in PostgreSQL, MySQL or Cassandra
- **Workers connect out** to the server, from wherever they run

<!--
Source: PPT slide 21.

The original listed MySQL as the default store and "running locally in Docker
containers." `temporal server start-dev` is simpler than either: one binary,
SQLite in memory unless you pass `--db-filename`, UI included on the gRPC port
plus 1000 (7233 and 8233 by default). Mention Docker Compose only if someone
asks how to run the full cluster locally. On Linux or Windows, install the CLI
from the Temporal docs instead of Homebrew.

The Java side is a JDK and a build tool. The SDK is one dependency,
`io.temporal:temporal-sdk` (this Deck is proven against 1.40.0), plus
`io.temporal:temporal-testing` for tests; Maven and Gradle both work.
temporalio/samples-java builds with Gradle and needs Java 17 or later; its
`HelloActivity` starts a Worker and a Workflow in one process, prints the
result and exits, which is the fastest proof that the dev server is up.

Today's Workshops use Java 21 and Maven, one `pom.xml` per project, with the
Worker and the starter as separate entry points:
`mvn -q compile exec:java@worker` runs the Worker and keeps polling,
`mvn -q compile exec:java@starter` starts a Workflow.
Two processes is the real shape of an application, and it is what makes the
kill-the-Worker demo work.

On the stores: PostgreSQL and MySQL are the usual choices for self-hosting.
Cassandra is supported but it is not what you want on a laptop.

The original slide also said Workers are polyglot. That means a Java Workflow
can call an Activity that another team's Worker runs, written with another
SDK. Say it only if the room has a mixed stack; this Workshop is Java end to
end.
-->
