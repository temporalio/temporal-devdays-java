package io.temporal.devdays.essentials.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs the Workflow from LoanWorkflow.java and LoanWorkflowImpl.java (10-queries.md). */
class QueriesTest {
  private TestWorkflowEnvironment env;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    Worker worker = env.newWorker("loans");
    worker.registerWorkflowImplementationTypes(LoanWorkflowImpl.class);
    env.start();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  @Test
  void aQueryReadsWorkflowStateAndAddsNothingToEventHistory() {
    WorkflowClient client = env.getWorkflowClient();
    LoanWorkflow loan =
        client.newWorkflowStub(
            LoanWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("loans").setWorkflowId("loan-1").build());
    assertEquals(
        new LoanResult("loan-1", "approved"),
        loan.process(new Loan("loan-1")),
        "LoanWorkflowImpl.java: process should return the approved loan");
    int before = client.fetchHistory("loan-1").getEvents().size();

    // Queries still answer after the Workflow has completed.
    for (int i = 0; i < 5; i++) {
      assertEquals(
          "approved",
          loan.getLoanStatus(),
          "LoanWorkflowImpl.java: getLoanStatus should return the current status field");
    }

    assertEquals(
        before,
        client.fetchHistory("loan-1").getEvents().size(),
        "10-queries.md: a Query is not an Event, so querying should not grow Event History");
  }
}
