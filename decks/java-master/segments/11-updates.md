---
layout: section
---

<ZiggyHero scene="update">

# TypeScript Updates

Synchronous write with result

</ZiggyHero>

<!--
Source: not in the original PowerPoint, which went from Queries straight to the
quiz. Ported from the Python Deck.

Updates complete the set: Signals write, Queries read, Updates do both.

If the room has a request/response use case (an API that must tell the caller
whether the change worked), this is the segment that answers "do I need a
database next to my Workflow for that?" No.
-->

---
layout: code-walkthrough
heading: TypeScript Updates
notes:
  - "<code>defineUpdate</code> declares a message the caller <strong>waits on</strong>: return type first, then arguments"
  - Unlike a Signal it returns a value, and unlike a Query it can change state
  - It can be async and await Activities. The caller waits until it finishes
  - Change state, then return the result to the caller
  - "The <code>validator</code> runs first, before anything is written to history"
  - Throw to <strong>reject</strong> the Update. The caller gets an error; history is untouched
---

::code::

```ts {all|1|8|9|10-11|14|15-17}
export const withdraw = defineUpdate<number, [number]>("withdraw");

export async function accountWorkflow(opening: number): Promise<void> {
  let balance = opening;

  setHandler(
    withdraw,
    async (amount: number) => {
      await debitAccount(amount);
      balance -= amount;
      return balance;
    },
    {
      validator: (amount: number) => {
        if (amount > balance) {
          throw new Error(`Insufficient funds: balance is ${balance}`);
        }
      },
    },
  );

  await condition(() => balance === 0);
}
```

<!--
Source: not in the original PowerPoint. Ported from the Python Deck.
Code: snippets/src/updates.workflows.ts, run by
snippets/tests/runtime/updates.test.ts. `debitAccount` is an Activity stub from
`proxyActivities`, off the slide.

The scenario: an ATM or a payments API withdraws from an account and must tell
the customer the new balance, or refuse the withdrawal outright. A Signal
cannot answer and a Query cannot change the balance.

- **Build 1 - `defineUpdate`.** Module level, like Signals and Queries. The
  string is the Update name; `<number, [number]>` is what the handler returns
  (the new balance) and the tuple of what it takes (the amount).
- **Build 2 - the handler.** It returns a number. That is the point: a Signal
  returns nothing and a Query must not change state; an Update does both.
- **Build 3 - async work.** An Update handler can `await` Activities, Timers and
  `condition`. The caller's `executeUpdate` waits until the handler returns, so
  the balance it gets back reflects a debit that actually posted.
- **Build 4 - change and answer.** The return value goes back to the caller and
  is recorded in history as `WorkflowExecutionUpdateCompleted`.
- **Build 5 - the validator.** Passed as an option to `setHandler`. It runs
  before the Update is accepted, takes the same arguments, and must be
  synchronous: it must not change state or await anything.
- **Build 6 - reject.** Throwing in the validator rejects the Update: the caller
  gets `WorkflowUpdateFailedError`, and nothing is written to history. After
  acceptance it is different: throw `ApplicationFailure` in the handler to fail
  just that Update. A plain `Error` or a `TypeError` does not fail the Update:
  it fails the Workflow Task, which retries while the caller waits.

One gotcha worth saying, and in a bank it is the one that matters: the
validator runs when the Update arrives, but the handler awaits an Activity
before it subtracts. JavaScript is single-threaded, yet handlers still
interleave at every `await`: on a balance of 100, two concurrent `withdraw(80)`
calls can both pass the validator, both debit, and leave the account at -60. The fix:
serialize the handlers with a lock and re-check the balance once you hold it.
The TypeScript docs use `Mutex` from the `async-mutex` package, which is safe in
Workflow code; a `busy` flag with `await condition(() => !busy)` does the same
job with no dependency (snippets/src/updates.workflows.ts,
`lockedAccountWorkflow`).
-->

---
layout: default
---

# Signals, Queries, and Updates

```ts
const balance = await handle.executeUpdate(withdraw, { args: [50] });
```

<div class="three-way">

|                     | Signal              | Query          | Update                     |
| ------------------- | ------------------- | -------------- | -------------------------- |
| **Changes state**   | Yes                 | No             | Yes                        |
| **Returns a value** | No                  | Yes            | Yes                        |
| **Caller waits**    | Until it's recorded | For the answer | Until the handler finishes |
| **Can be rejected** | No                  | N/A            | Yes, by a validator        |
| **In history**      | Yes                 | No             | Yes, if accepted           |
| **Needs a Worker**  | No                  | Yes            | Yes                        |

</div>

<style>
/* table-compare splits two value columns; this table has three. */
.three-way th:not(:first-child),
.three-way td:not(:first-child) {
  width: 27%;
}
</style>

<!--
Source: not in the original PowerPoint. Ported from the Python Deck.
Code: snippets/src/updates.client.ts, run by
snippets/tests/runtime/updates.test.ts.

The Client line: `executeUpdate` sends the Update and waits for the result,
here the balance after the withdrawal.
`startUpdate` with `waitForStage: WorkflowUpdateStage.ACCEPTED` returns a handle
as soon as the Update is accepted, if the caller wants to do something else
while the handler runs; `await updateHandle.result()` later. There is also
Update-with-Start (`client.workflow.executeUpdateWithStart`), which starts the
Workflow if it is not running yet and sends the Update in one call: the
standard shape for "open the account if needed, then make the deposit".

Read the "Needs a Worker" row out loud. A Signal is accepted by the Server even
if every Worker is down; it waits in history. Queries and Updates need a Worker
to answer, so a caller blocked on an Update with no Workers just waits. Wrap the call in `client.withDeadline(...)` or
`client.withAbortSignal(...)` to bound it. Use Signals for fire-and-forget (the
wire approval), Updates when the caller needs to know (did the withdrawal go
through, and what is left).

Updates have their own limits: at most 10 in flight at once per Workflow, and
2,000 per run (rejected ones do not count). Continue-As-New resets the count.
-->
