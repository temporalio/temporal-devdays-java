# Deck snippets

The code slides from `../segments/`, as Java you can run. Each source under
`src/main/java/io/temporal/devdays/essentials/` holds one or more slides' code
between `// slide:` and `// end slide` markers, plus whatever scaffolding it
needs to compile and run (interfaces, stub Activities). One package per topic,
so class names never clash:

```java
// slide: 09-signals.md "Java Signals"
...the code exactly as the slide shows it...
// end slide
```

The quoted anchor is any text in the segment before the code block, usually the
walkthrough's `heading:`. The region, dedented, must equal the first `java`
block after it.

A slide that shows several files in one block (a Workflow interface, then its
implementation) is split across sources: each file's share sits between
`// part: <segment.md> "<anchor>" <n>` and `// end part`, and the block must
equal the parts in order, joined by one blank line.

```bash
mvn test        # Java 21, Maven 3.9; no Temporal server needed
```

Four kinds of check, all in `mvn test`:

- Compiling `src/main` type-checks every snippet against the Temporal Java SDK
  1.40.0.
- `deck/SlidesMatchTest` fails when a slide and its snippet (or its parts)
  drift apart. Edit one, edit the other.
- `deck/SlidesParseTest` parses every Java block in the Deck with JavaParser,
  including the fragments no snippet runs. A line that is only `...` stands for
  elided code.
- `deck/WalkthroughsTest` checks every `code-walkthrough` slide's bubbles
  against its highlights: one note per step, no step starting or ending on a
  blank line, and any `<code>` a note names must be on a lit line. When a note
  mentions code that is deliberately elsewhere, write it
  `<code class="ref">run</code>`: it looks the same and is not checked.
- The runtime tests, one package per topic under `src/test/java`, run the
  snippets on `TestWorkflowEnvironment` and assert what the speaker notes
  claim.

The tests prove the right lines are lit. To prove the rendered bubble sits on
them, run the browser check against a dev server from `decks/java-master`:

```bash
npm run dev                    # terminal 1
npm run check:walkthroughs     # terminal 2; SLIDEV_URL=... if not on :3030
```

Both run in CI on any change to the Deck
(`.github/workflows/java-master-deck.yml`).
