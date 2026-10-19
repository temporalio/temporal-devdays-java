package io.temporal.devdays.greeting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * One group of tests per Challenge, tagged challengeN (ADR 0002). The assertion
 * messages are what a learner sees on a failed Check: name the file, the method
 * and the TODO.
 */
class GreetingWorkflowTest {
    private TestWorkflowEnvironment env;
    private Worker worker;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        worker = env.newWorker(Shared.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    @Test
    @Tag("challenge1")
    void greetsByName() {
        worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
        env.start();
        GreetingWorkflow workflow = env.getWorkflowClient().newWorkflowStub(
                GreetingWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(Shared.TASK_QUEUE).build());

        assertEquals("Hello, Temporal!", workflow.greet("Temporal"),
                "GreetingWorkflowImpl.greet must return what composeGreeting returns (TODO 1)");
    }
}
