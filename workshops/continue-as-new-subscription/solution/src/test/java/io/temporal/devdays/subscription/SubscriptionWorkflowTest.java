package io.temporal.devdays.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.TerminatedFailure;
import io.temporal.failure.TimeoutFailure;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkflowImplementationOptions;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The Track's Check (ADR 0002). A failing assertion's message is exactly what a
 * learner sees when Check fails, so every message names the file, the method
 * and the TODO.
 *
 * The in-memory test server never suggests Continue-As-New, so this starts a
 * real dev server with the same two settings the Track's dev server uses,
 * scaled down further so the test is quick: the Service suggests
 * Continue-As-New at 20 Events and terminates a run past 100. A billing cycle
 * adds about 11 Events, so a run that never rolls over is terminated around
 * cycle 9, and a run that does rolls over every 2 cycles.
 */
class SubscriptionWorkflowTest {
    private static final int CYCLES = 12;

    private static DevServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = DevServer.start(
                "limit.historyCount.suggestContinueAsNew=20",
                "limit.historyCount.error=100");
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    @Tag("challenge1")
    void subscriptionRollsOverAndFinishes() {
        WorkflowClient client = server.client();
        String taskQueue = "test-" + UUID.randomUUID();
        String subscriptionId = "test-" + UUID.randomUUID().toString().substring(0, 8);

        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(taskQueue);
        // Fail the Workflow on any exception instead of retrying the Task, so a
        // half-finished TODO fails the test rather than hanging it.
        worker.registerWorkflowImplementationTypes(
                WorkflowImplementationOptions.newBuilder().setFailWorkflowExceptionTypes(Exception.class).build(),
                SubscriptionWorkflowImpl.class);
        worker.registerActivitiesImplementations(new SubscriptionActivitiesImpl());
        factory.start();

        try {
            SubscriptionWorkflow workflow = client.newWorkflowStub(
                    SubscriptionWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(taskQueue)
                            .setWorkflowId(subscriptionId)
                            // Generous: a Sandbox that has just booted can be slow. A run that
                            // never rolls over is terminated by the Service long before this.
                            .setWorkflowExecutionTimeout(Duration.ofSeconds(60))
                            .build());
            String firstRunId = WorkflowClient.start(workflow::run, SubscriptionInput.start(subscriptionId, CYCLES))
                    .getRunId();

            SubscriptionResult result;
            try {
                result = WorkflowStub.fromTyped(workflow).getResult(SubscriptionResult.class);
            } catch (WorkflowFailedException e) {
                if (e.getCause() instanceof TerminatedFailure) {
                    fail("The Temporal Service suggested Continue-As-New and the subscription never rolled over, "
                            + "so its history hit the limit and the run was terminated. TODO 1 in "
                            + "SubscriptionWorkflowImpl.java (run): when Workflow.getInfo().isContinueAsNewSuggested(), "
                            + "call Workflow.continueAsNew(...).");
                }
                if (e.getCause() instanceof TimeoutFailure) {
                    fail("The subscription rolled over but never reached " + CYCLES + " cycles: each new run started "
                            + "counting from 0 again. TODO 1 in SubscriptionWorkflowImpl.java (run): pass "
                            + "cyclesCompleted in the SubscriptionInput you hand to Workflow.continueAsNew(...).");
                }
                fail("The subscription failed: " + e.getCause() + ". Check TODO 1 in SubscriptionWorkflowImpl.java (run).");
                return;
            }

            assertEquals(CYCLES, result.totalCycles(),
                    "Asked for " + CYCLES + " cycles and the subscription billed " + result.totalCycles()
                            + ". Check TODO 1 in SubscriptionWorkflowImpl.java (run).");

            WorkflowExecutionStatus firstRun = client
                    .newUntypedWorkflowStub(subscriptionId, Optional.of(firstRunId), Optional.empty())
                    .describe()
                    .getStatus();
            assertEquals(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW, firstRun,
                    "The subscription finished without ever rolling over. TODO 1 in SubscriptionWorkflowImpl.java "
                            + "(run): call Workflow.continueAsNew(...) when the Service suggests it.");
        } finally {
            factory.shutdownNow();
        }
    }
}
