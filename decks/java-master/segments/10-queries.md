---
layout: section
---

<ZiggyHero scene="query">

# TypeScript Queries

Reading state out of a running Workflow.

</ZiggyHero>

<!--
Source: PPT slides 80-85.

The mirror of Signals, and a short segment. Signals deliver data in, Queries
read data out.
-->

---
layout: code-walkthrough
heading: TypeScript Queries
notes:
  - State the Query will expose. A plain local variable
  - The Workflow body updates it as it goes
  - "Same pattern as Signals: <code>defineQuery</code> at module level. The type parameter is the <strong>return</strong> type"
  - Unlike Signals, Queries <strong>return a value</strong>. A read-only view into Workflow state
  - The return value can be anything serializable, not just a string. Queries can take arguments too
---

::code::

```ts {all|6|9|3|7|3}
import { defineQuery, setHandler } from "@temporalio/workflow";

export const getLoanStatus = defineQuery<string>("getLoanStatus");

export async function loanWorkflow(loan: Loan): Promise<LoanResult> {
  let status = "underwriting";
  setHandler(getLoanStatus, () => status);

  status = "approved";
  return { loanId: loan.loanId, status };
}
```

<!--
Source: PPT slides 81-85.
Code: snippets/src/queries.workflows.ts, run by
snippets/tests/runtime/queries.test.ts.

The scenario: a loan application moves through underwriting to a decision, and
the customer checks progress in the bank's app. Each refresh of that page is a
Query.

- **Build 1 - the state.** Any variable the handler can close over.
- **Build 2 - the update.** Nothing re-registers the handler. It is a closure
  over `status`, so it reads whatever the variable holds at the moment it is
  asked: a live reference, not a snapshot.
- **Build 3 - `defineQuery`.** Same shape as a Signal, with one difference worth
  pointing at: `<string>` here is the *return* type. Queries return data;
  Signals deliver it.
- **Build 4 - the handler.** A synchronous function. It must not change state
  and must not `await` anything; the types reject a handler that returns a
  Promise. A Query that throws fails that Query for the caller, not the
  Workflow.
- **Build 5 - the types.** An object works as well as a string. And a Query may
  take arguments: `defineQuery<number, [number]>("getMonthlyPayment")` declares a
  Query that takes a term in months and returns the payment.

Why it matters: a Client can send a Query at any point during execution without
waiting for completion. The customer's app, a branch officer's screen, a
support tool. Queries
also answer after the Workflow has completed. And a Query costs nothing in
history. It is not an Event, so querying a Workflow a thousand times does not
grow its history by a byte.

Demo line: `temporal workflow query --workflow-id <id> --name getLoanStatus`.
-->
