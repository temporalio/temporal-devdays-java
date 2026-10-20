# Temporal DevDays — Java

Vocabulary for the Java DevDays content program: the workshops we deliver to
customers, assembled per customer request. Shares its language with
`temporalio/temporal-devdays-python` and `temporalio/temporal-devdays-ts`.

## Language

**DevDays**:
The content program itself — the Workshops, their Ports across languages, and
the Decks they are delivered from. Names the ongoing body of work, never a
single event.
_Avoid_: Dev Days (the two-word form names the events), the program, DevDay

**Dev Day**:
A live, instructor-led delivery event run for one customer, assembled from one
or more Workshops chosen for that customer's request and that room's mix of
experience. Never identified by customer name in this repo.
_Avoid_: workshop (a Dev Day is the event, not the material), training, session

**Workshop**:
A reusable teaching unit on one topic — its Deck, its Track, and its Exercise
and Solution code. The thing that gets assembled into a Dev Day and Ported
between languages.
_Avoid_: course, lab, content, module

**Level**:
The experience a Workshop is written for — **Foundations** (new to Temporal) or
**Advanced** (already building with it). Every Workshop has exactly one; a Dev
Day serves a mixed room by combining Workshops of different Levels.
_Avoid_: difficulty, tier, 101/201

**Authoring**:
Producing a Workshop in Java, either by Port or by writing it new for a
customer request. One Authoring, one cost record, reported as two figures: the
Deck cost and the Track cost.
_Avoid_: Deck Port cost (a new Deck is not a Port), Lab cost (it is a Track)

**Port**:
The kind of Authoring that recreates an existing Workshop from another Temporal
SDK language, preserving the narrative — its story, Challenges and TODO
numbering — while rewriting the code idiomatically.
_Avoid_: translation, rewrite, conversion

**Track**:
The Instruqt artifact for a Workshop — a `track.yml`, a `config.yml`, and its
Challenges. One Track per Workshop per language.
_Avoid_: lab, workshop, course

**Challenge**:
One numbered step within a Track, with an assignment and its lifecycle scripts.
_Avoid_: exercise (that word names the code tree), lesson, step

**Check**:
The gate on a Challenge: it fails until that Challenge's work is done and says
what is missing and where.
_Avoid_: validation, verify

**Deck**:
The Slidev presentation delivered alongside a Workshop.
_Avoid_: slides, presentation

**Exercise**:
The code tree a learner starts from, with the work left undone as numbered TODOs.
Core TODOs are required to finish; Stretch TODOs are optional extras for learners
who finish early, and nothing later depends on them.
_Avoid_: starter, skeleton, template

**Solution**:
The completed counterpart of an Exercise, structurally identical to it.
_Avoid_: answer, final, complete

**Sandbox Image**:
The container image Instruqt boots for a Track, holding the toolchain, the
editor, a local Temporal dev server, and the pre-warmed dependency cache.
_Avoid_: box, environment, VM
