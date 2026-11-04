---
layout: section
---

<ZiggyHero scene="query">

# Java Queries

Reading state out of a running Workflow.

</ZiggyHero>

<!--
Source: PPT slides 80-85.

The mirror of Signals, and a short segment. Signals deliver data in, Queries
read data out.
-->

---
layout: code-walkthrough
heading: Java Queries
notes:
  - State the Query will expose. A plain field
  - The Workflow method updates it as it goes
  - "Same pattern as Signals: <code>@QueryMethod</code> on the interface. The return type is what the caller gets back"
  - Unlike Signals, Queries <strong>return a value</strong>. A read-only view into Workflow state
  - The return value can be anything serializable, not just a String. Queries can take arguments too
---

::code::

```java {all|11|15|6-7|19-21|7}
@WorkflowInterface
public interface LoanWorkflow {
  @WorkflowMethod
  LoanResult process(Loan loan);

  @QueryMethod
  String getLoanStatus();
}

public class LoanWorkflowImpl implements LoanWorkflow {
  private String status = "underwriting";

  @Override
  public LoanResult process(Loan loan) {
    status = "approved";
    return new LoanResult(loan.loanId(), status);
  }

  @Override
  public String getLoanStatus() {
    return status;
  }
}
```

<!--
Source: PPT slides 81-85.
Code: snippets/src/main/java/io/temporal/devdays/essentials/queries/LoanWorkflow.java
and LoanWorkflowImpl.java, run by QueriesTest.

The scenario: a loan application moves through underwriting to a decision, and
the customer checks progress in the bank's app. Each refresh of that page is a
Query.

- **Build 1 - the state.** Any field on the implementation.
- **Build 2 - the update.** Nothing re-registers the handler. It reads the
  field, so it returns whatever the field holds at the moment it is asked: a
  live view, not a snapshot.
- **Build 3 - `@QueryMethod`.** Same shape as a Signal, with one difference
  worth pointing at: the return type. The SDK refuses a `void` Query method.
  Queries return data; Signals deliver it.
- **Build 4 - the handler.** It must not change state and must not block: no
  Activities, no `Workflow.sleep`, no `Workflow.await`. A Query that throws
  fails that Query for the caller (`WorkflowQueryException`), not the Workflow.
- **Build 5 - the types.** A record works as well as a String. And a Query may
  take arguments: `@QueryMethod double getMonthlyPayment(int termMonths)`
  declares a Query that takes a term in months and returns the payment.

Why it matters: a Client can send a Query at any point during execution without
waiting for completion. The customer's app, a branch officer's screen, a
support tool. Queries also answer after the Workflow has completed. And a Query
costs nothing in history. It is not an Event, so querying a Workflow a thousand
times does not grow its history by a byte.

Demo line: `temporal workflow query --workflow-id <id> --name getLoanStatus`.
-->
