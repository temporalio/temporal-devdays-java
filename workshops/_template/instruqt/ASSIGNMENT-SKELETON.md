---
slug: <challenge-slug>
type: challenge
title: "N. <Title>"
teaser: <One sentence shown in the Challenge list.>
notes:
- type: text
  contents: |-
    # <The question this Challenge answers, e.g. "What happens when the charge lands and still fails?">

    <Two or three short sentences of business context: what breaks for the
    customer if this code is wrong.>
tabs:
- title: Exercise
  type: service
  hostname: workshop
  port: 8080
  path: /?folder=/root/workshop/<slug>
- title: Worker
  type: terminal
  hostname: workshop
  workdir: /root/workshop/<slug>/exercise
- title: Client
  type: terminal
  hostname: workshop
  workdir: /root/workshop/<slug>/exercise
- title: Temporal UI
  type: service
  hostname: workshop
  port: 8233
  path: /
difficulty: basic
timelimit: 600
---

<!--
Fill every section. Delete this comment. Voice: imperative, short, no em-dashes.
Refer to other Challenges only in "Leave the rest alone", in one line.
-->

# Goal

<One sentence: the change and what it makes true.>

**Why it matters:** <one line of business consequence.>

<!-- First Challenge only: explain every tab, one line each, with its button.
List exactly the tabs in `tabs:` above, in order. Delete this section in later
Challenges. -->

# Your tabs

- [button label="Exercise" background="#444CE7"](tab-0) VS Code in the browser, open on this Workshop. You edit
  the code under `exercise/`. It saves as you type.
- [button label="Worker" background="#444CE7"](tab-1) a terminal in `exercise/`. Run `run-worker` here. It
  restarts the Worker every time you save.
- [button label="Client" background="#444CE7"](tab-2) a terminal in `exercise/`. Run `run-starter` here to start
  a Workflow.
- [button label="Temporal UI" background="#444CE7"](tab-3) the Temporal Web UI of the local dev server:
  <what this Workshop's learner will look at there.>

# Where

<!-- snippet: exercise/src/main/java/io/temporal/devdays/<pkg>/<File>.java TODO N -->
Open `<File>.java` in the [button label="Exercise" background="#444CE7"](tab-0)
tab. Press `Ctrl+P` (`Cmd+P` on a Mac), type the file name, then `Ctrl+G` to jump to a line.
Read lines A-B, <what this code is>. You change them in **Change it**, below:

```java
<filled in by: python3 scripts/check-snippets.py workshops/<slug> --fix>
```

<!-- Every snippet says whether the learner acts now or only reads, and ends
with a "Look at" line: the one thing to notice, why it is wrong or matters,
and which TODO fixes it. -->

**Look at** <the line or construct that matters>. <What it does today and why
that breaks the business outcome.> TODO N <changes what>.

# Change it

- [ ] <First concrete edit, in words, not the answer.>
- [ ] <Second, if the change has two parts.>

Change only the lines between `// TODO N start` and `// TODO N end`. Leave the
other TODOs alone; they are the next Challenges.

<details>
<summary>Hint</summary>

<A generic Java example of the construct, not the answer.>

</details>

<details>
<summary>Show solution</summary>

<The finished lines, copied from solution/.../<File>.java.>

</details>

# Run it

The Worker restarts when you save. In the
[button label="Client" background="#444CE7"](tab-2) tab:

```bash,run
run-starter <args>
```

In the [button label="Worker" background="#444CE7"](tab-1) tab you should see:

```bash,nocopy
<expected output, copied from a real run, never typed by hand>
```

Open the [button label="Temporal UI" background="#444CE7"](tab-3) tab and click
the newest Workflow. If it isn't there, click the refresh button: the list does
not always update on its own. <What to look for in the Event History.>

# Checkpoint

Click **Check**. It passes when <the observable outcome>. If it fails, the
message names the file and the TODO still to do.
