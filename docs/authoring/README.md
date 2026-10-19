# Authoring cost records

One file per Authoring, holding two figures: the Deck cost and the Track cost.
Measure each part over the window it was built in:

```sh
node scripts/authoring-cost.mjs -Users-nikk-source-temporal-java-temporal-devdays-java \
  --part deck --since 2026-10-01 --until 2026-10-03 --label "<Workshop>" \
  > docs/authoring/<slug>-deck.md

node scripts/authoring-cost.mjs -Users-nikk-source-temporal-java-temporal-devdays-java \
  --part track --since 2026-10-04 --label "<Workshop>" \
  > docs/authoring/<slug>-track.md
```

Put each total in the README index's **Deck cost ($)** and **Track cost ($)**
columns, and the script's `README model:` line in the **Model** column. That
line names every model that spent tokens, heaviest share of dollars first
(`Opus 5.5 (92%), Haiku 4.5 (8%)`). A model the script cannot price shows as
`(unpriced)`; add its rates to `RATES` and re-run before publishing the row.

## Windows

`--since` and `--until` take a date or an exact ISO timestamp with a timezone
(`2026-09-29T13:15:53Z`). A bare `--until` date includes that whole day; a
timestamp is exclusive. Use timestamps whenever the Deck and the Track, or
Authoring and other work, share a day.

Keep a `<slug>-log.md` next to the records with the start and end timestamp of
each phase as you go. It is the source for the windows, and it survives after
the transcripts age out. When a part spans several windows, run the script once
per window and sum the totals.

Workshops built by parallel subagents share one window, so a time window cannot
tell them apart. Measure each from its own subagent transcript instead, with
`--transcript agent-<id>` (any substring of the transcript's path), and cost the
main session's shared work as `--part other`.

Work done during an Authoring that is not the Workshop itself — template or
tooling changes, a skill fix — is measured with `--part other` and recorded in
the log, so it stays out of the two README figures.

## Rules

The slug is the **working directory** under `~/.claude/projects`, so work an
Authoring from one repo, editing any other by absolute path. Otherwise its cost
splits across transcripts. Transcripts are local and age out: **run the script
at the end of the Authoring**, not weeks later.

The script prices only models listed in its `RATES` table (keyed by model id
without a date suffix) and warns about any other model it finds. Add a model's
published rates there before trusting a total.

Figures are agent tokens only — a floor. Fill in the human-time table by hand.
