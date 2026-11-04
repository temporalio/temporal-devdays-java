---
layout: section
---

<ZiggyHero scene="update">

# Java Updates

Synchronous write with result

</ZiggyHero>

<!--
Source: not in the original PowerPoint, which went from Queries straight to the
quiz.

Updates complete the set: Signals write, Queries read, Updates do both.

If the room has a request/response use case (an API that must tell the caller
whether the change worked), this is the segment that answers "do I need a
database next to my Workflow for that?" No.
-->

---
layout: code-walkthrough
heading: Java Updates
notes:
  - "<code>@UpdateMethod</code> declares a message the caller <strong>waits on</strong>"
  - Unlike a Signal it returns a value, and unlike a Query it can change state
  - "<code>@UpdateValidatorMethod</code> guards the Update it names"
  - "Same arguments as the Update, returns <code>void</code>. It runs before anything is written to history"
---

::code::

```java {all|6-7|7|9|10}
@WorkflowInterface
public interface AccountWorkflow {
  @WorkflowMethod
  void run(int opening);

  @UpdateMethod
  int withdraw(int amount);

  @UpdateValidatorMethod(updateName = "withdraw")
  void validateWithdraw(int amount);
}
```

<!--
Source: not in the original PowerPoint.
Code: snippets/src/main/java/io/temporal/devdays/essentials/updates/AccountWorkflow.java,
run by UpdatesTest.

The scenario: an ATM or a payments API withdraws from an account and must tell
the customer the new balance, or refuse the withdrawal outright. A Signal
cannot answer and a Query cannot change the balance.

- **Build 1 - `@UpdateMethod`.** On the interface, like Signals and Queries.
  The method name, `withdraw`, is the Update name; `@UpdateMethod(name = "...")`
  overrides it.
- **Build 2 - the return type.** `int`: the new balance. That is the point: a
  Signal returns nothing and a Query must not change state; an Update does both.
- **Build 3 - `@UpdateValidatorMethod`.** Optional. `updateName` ties it to the
  Update it checks, by name.
- **Build 4 - the validator's shape.** The same parameters as the Update, and
  the SDK refuses one that does not return `void`. It answers one question:
  accept this Update or not.

This needed two slides where Signals and Queries needed one: the interface
carries both handlers' declarations. The next slide is the implementation.
-->

---
layout: code-walkthrough
heading: "Java Updates: the implementation"
notes:
  - "<code>@WorkflowInit</code>: the constructor sets state before any handler runs"
  - The Workflow runs until the account is empty
  - It can call Activities. The caller waits until the handler returns
  - Change state, then return the result to the caller
  - "The validator runs first, before anything is written to history"
  - Throw to <strong>reject</strong> the Update. The caller gets an error; history is untouched
---

::code::

```java {all|5-8|12|17|18-19|23|24-25}
public class AccountWorkflowImpl implements AccountWorkflow {

  private int balance;

  @WorkflowInit
  public AccountWorkflowImpl(int opening) {
    balance = opening;
  }

  @Override
  public void run(int opening) {
    Workflow.await(() -> balance == 0);
  }

  @Override
  public int withdraw(int amount) {
    activities.debitAccount(amount);
    balance -= amount;
    return balance;
  }

  @Override
  public void validateWithdraw(int amount) {
    if (amount > balance) {
      throw new IllegalArgumentException("Insufficient funds: balance is " + balance);
    }
  }
}
```

<!--
Source: not in the original PowerPoint.
Code: snippets/src/main/java/io/temporal/devdays/essentials/updates/AccountWorkflowImpl.java,
run by UpdatesTest. `activities` is an Activity stub from
`Workflow.newActivityStub`, a field off the slide.

- **Build 1 - `@WorkflowInit`.** A constructor that takes the Workflow method's
  arguments. The SDK calls it before the Workflow method and before any Update
  or Signal handler, so the validator never sees a balance of zero just because
  the Update arrived in the same Workflow Task as the start. It is marked
  `@Experimental` in SDK 1.40.0.
- **Build 2 - the Workflow method.** It just waits. All the work happens in the
  handler.
- **Build 3 - blocking work.** An Update handler can call Activities, sleep, and
  `Workflow.await`. The caller's `account.withdraw(50)` waits until the handler
  returns, so the balance it gets back reflects a debit that actually posted.
- **Build 4 - change and answer.** The return value goes back to the caller and
  is recorded in history as `WorkflowExecutionUpdateCompleted`.
- **Build 5 - the validator.** It runs before the Update is accepted, takes the
  same arguments, and runs read-only. Calling an Activity, sleeping or awaiting
  from it fails the Workflow Task. Changing a field is not detected, and
  validators are skipped on replay, so a validator that mutates state silently
  diverges: don't.
- **Build 6 - reject.** Any exception thrown in the validator rejects the
  Update: the caller gets `WorkflowUpdateException`, and nothing is written to
  history. After acceptance it is different: throw an `ApplicationFailure` in
  the handler to fail just that Update. An `IllegalArgumentException` or a
  `NullPointerException` in the handler does not fail the Update: it fails the
  Workflow Task, which retries while the caller waits.

One gotcha worth saying, and in a bank it is the one that matters: the
validator runs when the Update arrives, but the handler calls an Activity
before it subtracts. The SDK runs one Workflow thread at a time, yet handlers
still interleave wherever one blocks: on a balance of 100, two concurrent
`withdraw(80)` calls can both pass the validator, both debit, and leave the
account at -60. The fix: serialize the handlers with a lock and re-check the
balance once you hold it. In Java that is `Workflow.newWorkflowLock()`:
`lock.lock()` at the top of the handler, `lock.unlock()` in a `finally`. It is
deterministic and safe in Workflow code; `synchronized` and the
`java.util.concurrent` locks are not
(snippets/src/main/java/io/temporal/devdays/essentials/updates/LockedAccountWorkflowImpl.java).
-->

---
layout: default
---

# Signals, Queries, and Updates

```java
int balance = account.withdraw(50);
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
Source: not in the original PowerPoint.
Code: snippets/src/main/java/io/temporal/devdays/essentials/updates/UpdatesClient.java,
run by UpdatesTest.

The Client line: calling the `@UpdateMethod` on the typed stub sends the Update
and waits for the result, here the balance after the withdrawal.
`WorkflowStub.fromTyped(account).startUpdate("withdraw", WorkflowUpdateStage.ACCEPTED, Integer.class, 50)`
returns a `WorkflowUpdateHandle` as soon as the Update is accepted, if the
caller wants to do something else while the handler runs; call `getResult()` on
it later. There is also Update-with-Start
(`WorkflowClient.executeUpdateWithStart` with a `WithStartWorkflowOperation`),
which starts the Workflow if it is not running yet and sends the Update in one
call: the standard shape for "open the account if needed, then make the
deposit".

Read the "Needs a Worker" row out loud. A Signal is accepted by the Server even
if every Worker is down; it waits in history. Queries and Updates need a Worker
to answer, so a caller blocked on an Update with no Workers just waits. Bound
it on your side, for example
`CompletableFuture.supplyAsync(() -> account.withdraw(50)).orTimeout(5, SECONDS)`.
Use Signals for fire-and-forget (the wire approval), Updates when the caller
needs to know (did the withdrawal go through, and what is left).

Updates have their own limits: at most 10 in flight at once per Workflow, and
2,000 per run (rejected ones do not count). Continue-As-New resets the count.
-->
