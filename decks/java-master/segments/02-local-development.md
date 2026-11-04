---
layout: section
---

<ZiggyHero scene="demo">

# Demo

</ZiggyHero>

<!--
Source: PPT slide 20. Ported from the Python Deck.

Run a Workflow live if you have one ready: start a Worker, start a few
Workflows, and open the Web UI at localhost:8233. Kill the Worker mid-run and
start it again. The Workflow carries on from where it stopped. That is the
whole pitch in thirty seconds, and it lands harder than any slide.

If you have nothing prepared, the hello-world project on the next slide is
enough: add a `sleep("30 seconds")` to its Workflow, kill the Worker during the
sleep, restart it, and watch the Workflow finish.
-->

---
layout: default
---

# Local development

```bash
brew install temporal
temporal server start-dev

npx @temporalio/create@latest ./my-app --sample hello-world
cd my-app && npm run start.watch   # the Worker
npm run workflow                   # in a second terminal: start a Workflow
```

- **MIT-licensed open source.** Same server here, on-prem, or in Temporal Cloud
- **One binary**, with the Web UI at `localhost:8233`
- **Self-hosted state** in PostgreSQL, MySQL or Cassandra
- **Workers connect out** to the server, from wherever they run

<!--
Source: PPT slide 21. Ported from the Python Deck.

The original listed MySQL as the default store and "running locally in Docker
containers." `temporal server start-dev` is simpler than either: one binary,
SQLite in memory unless you pass `--db-filename`, UI included on the gRPC port
plus 1000 (7233 and 8233 by default). Mention Docker Compose only if someone
asks how to run the full cluster locally. On Linux or Windows, install the CLI
from the Temporal docs instead of Homebrew.

`@temporalio/create` scaffolds a project from any sample in
temporalio/samples-typescript and runs `npm install` for you. The hello-world
sample's scripts are the three you need: `npm run start.watch` runs the Worker
under nodemon and restarts it on every save, `npm start` runs it once, and
`npm run workflow` runs the Client that starts a Workflow and prints its
result.

On the stores: PostgreSQL and MySQL are the usual choices for self-hosting.
Cassandra is supported but it is not what you want on a laptop.

The original slide also said Workers are polyglot. That means a TypeScript
Workflow can call an Activity that another team's Worker runs in another
language. Say it only if the room has a mixed stack; this Workshop is
TypeScript end to end.
-->
