# AGENTS.md

This repo holds the Java half of the Temporal DevDays program: Workshops
(Deck, Track, Exercise and Solution) that are assembled into customer Dev Days.

Read [`CONTEXT.md`](CONTEXT.md) before writing anything, and use its terms
exactly — Workshop, Dev Day, Track, Challenge, Check, Deck, Exercise, Solution,
Level, Authoring, Port. Read [`docs/adr/`](docs/adr/) before moving content
between repos or changing the Sandbox Image.

## House rules

- **No customer names**, anywhere: code, Decks, Tracks, ADRs, commit messages.
  Refer to "a customer Dev Day" and a date instead.
- **One SDK per Workshop.** A Workshop teaches Java and never names the other
  SDKs or other-language versions of itself. `scripts/verify-content.sh` fails
  otherwise.
- **Exercise and Solution are structurally identical.** Same files, same names,
  same tests. The Exercise differs only where a numbered TODO is left undone.
- **TODOs are numbered** (`// TODO 1 start` ... `// TODO 1 end`, `TODO 2a`,
  `TODO 2b`) and match the Track's Challenge assignments. Stretch TODOs are
  marked `STRETCH` and nothing later depends on them.
- **Every Workshop has one Level**: Foundations or Advanced.
- **Solutions are proven**: `mvn test` in `solution/` must pass, using
  `TestWorkflowEnvironment`, or a dev server the test starts itself
  (`DevServer` in the template) when the in-memory server cannot reproduce
  the behaviour.
- **Tests are grouped per Challenge** with JUnit 5 `@Tag("challengeN")`, and
  their assertion messages are written for learners: name the file, the method
  and the TODO (ADR 0002).
- **Every PR that adds a Deck or a Workshop adds its README row** in the same
  PR. Fill in what the PR contains, mark the rest `Pending`, and run
  `bash scripts/check-readme-index.sh`. The README index check fails the PR
  otherwise.
- **The first Challenge explains every tab.** A `# Your tabs` section lists
  each tab in the Challenge's `tabs:`, with its button and one line on what it
  is for, worded for this Workshop. Never just say the buttons are clickable.
- **One change at a time: Observe, Learn, Do.** Each TODO gets its own
  section, in the order the learner makes them: open the file and show the
  code (Observe), say what it does today and why that matters (Learn), then the
  edit as a checkbox with its own hint and solution (Do). Never a "Where"
  section of snippets followed by a separate "Change it" list. Challenges that
  change no code say so in their heading ("Read the code").
- New Workshops start from `workshops/_template/`. Sandbox Images build `FROM`
  the shared `sandbox-base/` image (ADR 0003).

## Layout

```
sandbox-base/      the shared Sandbox base image, Check and Skip helpers
workshops/<slug>/
  exercise/        learner starts here (Maven project)
  solution/        completed counterpart
  instruqt/        the Track
  sandbox/         the Workshop's Sandbox Image (FROM sandbox-base)
decks/<slug>/      the Slidev Deck (none yet)
docs/adr/          decisions
docs/authoring/    cost records
scripts/           tooling
```

## Toolchain

Java 21, Maven 3.9, one `pom.xml` per Exercise and Solution tree, Temporal
Java SDK (`io.temporal:temporal-sdk`, `temporal-testing`), JUnit 5. Packages are
`io.temporal.devdays.<workshop>`. The Worker and starter run with
`mvn -q compile exec:java@worker` and `mvn -q compile exec:java@starter`.
