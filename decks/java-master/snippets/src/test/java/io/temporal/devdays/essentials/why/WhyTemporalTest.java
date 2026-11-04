package io.temporal.devdays.essentials.why;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 01-why-temporal.md "The Temporal version": retries are configuration. The Activity has no retry
 * loop, yet a payment service that fails three times still lets the order through on the fourth
 * attempt, and a fifth is never made.
 */
class WhyTemporalTest {
  private static final String TASK_QUEUE = "why-temporal";

  private TestWorkflowEnvironment env;
  private WorkflowClient client;

  @BeforeEach
  void setUp() {
    Services.INVENTORY.reset();
    Services.PAYMENTS.reset();
    Services.SHIPPING.reset();
    env = TestWorkflowEnvironment.newInstance();
    Worker worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(FulfillmentWorkflowImpl.class);
    worker.registerActivitiesImplementations(new FulfillmentActivitiesImpl());
    env.start();
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private FulfillmentWorkflow stub(String id) {
    return client.newWorkflowStub(
        FulfillmentWorkflow.class,
        WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).setWorkflowId(id).build());
  }

  @Test
  void threePaymentFailuresThenTheFourthAttemptSucceeds() {
    Services.PAYMENTS.failuresLeft.set(3);

    String tracking = stub("order-1").fulfill("order-1");

    assertEquals(
        "trk-order-1",
        tracking,
        "FulfillmentWorkflowImpl.java: the slide claims retries are configuration, so the order"
            + " ships after three payment failures");
    assertEquals(
        4,
        Services.PAYMENTS.calls.get(),
        "FulfillmentWorkflowImpl.java: setMaximumAttempts(4) should allow exactly four payment"
            + " attempts");
    // A retry reruns the whole Activity, so the reservation is repeated: the speaker notes say
    // why each service call must be idempotent by order ID.
    assertEquals(
        4,
        Services.INVENTORY.calls.get(),
        "FulfillmentActivitiesImpl.java: the notes claim a retry reruns the whole Activity,"
            + " reservation included");
    assertEquals(
        1,
        Services.SHIPPING.calls.get(),
        "FulfillmentActivitiesImpl.java: shipping runs once, on the attempt that succeeds");
  }

  @Test
  void givesUpAfterMaximumAttemptsAndNeverShips() {
    Services.PAYMENTS.failuresLeft.set(10);

    assertThrows(
        WorkflowFailedException.class,
        () -> stub("order-2").fulfill("order-2"),
        "FulfillmentWorkflowImpl.java: after setMaximumAttempts(4) the Activity failure should"
            + " fail the Workflow");
    assertEquals(
        4,
        Services.PAYMENTS.calls.get(),
        "FulfillmentWorkflowImpl.java: setMaximumAttempts(4) means a fifth attempt is never made");
    assertEquals(
        0,
        Services.SHIPPING.calls.get(),
        "FulfillmentActivitiesImpl.java: an order whose payment never succeeds is never shipped");
  }
}
