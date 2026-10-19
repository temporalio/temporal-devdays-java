package io.temporal.devdays.childworkflows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.ChildWorkflowFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkflowImplementationOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * One group of tests per Challenge, tagged challengeN (ADR 0002). The assertion
 * messages are what a learner sees on a failed Check: they name the file, the
 * method and the TODO.
 *
 * Challenges 1 to 3 are run and observe, so their tests prove what the shipped
 * Exercise already does. Challenge 4 is the one TODO.
 */
class ChildWorkflowsTest {
    private static final List<String> ITEMS = List.of("mug", "book", "lamp");

    private TestWorkflowEnvironment env;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        Worker worker = env.newWorker(Shared.TASK_QUEUE);
        // Fail the Workflow on any exception instead of retrying its task, so a
        // half-finished TODO fails the test rather than hanging it.
        worker.registerWorkflowImplementationTypes(
                WorkflowImplementationOptions.newBuilder().setFailWorkflowExceptionTypes(Throwable.class).build(),
                OrderWorkflowImpl.class,
                ShipmentWorkflowImpl.class,
                ChainWorkflowImpl.class,
                PackWorkflowImpl.class,
                PackItemWorkflowImpl.class);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    // --- Challenge 1: one child ---------------------------------------------------

    @Test
    @Tag("challenge1")
    void orderWaitsForItsShipmentChild() {
        Order order = newOrder("mug");
        OrderWorkflow workflow = stub(OrderWorkflow.class, "test-order-" + order.orderId());

        assertEquals("TRACK-" + order.orderId(), workflow.placeOrder(order),
                "OrderWorkflowImpl.placeOrder must return what its ShipmentWorkflow child returns");
        assertEquals("test-order-" + order.orderId(), parentOf("shipment-" + order.orderId()),
                "OrderWorkflowImpl.placeOrder must start shipment-<orderId> as its child");
    }

    // --- Challenge 2: a chain of children -----------------------------------------

    @Test
    @Tag("challenge2")
    void chainCountsItsLevels() {
        String root = "test-chain-" + shortId();
        ChainWorkflow workflow = stub(ChainWorkflow.class, root);

        assertEquals(3, workflow.run(new ChainRequest(3, false)),
                "ChainWorkflowImpl.run must return how many levels of children ran below it");
        assertEquals(root + "/2/1", parentOf(root + "/2/1/0"),
                "ChainWorkflowImpl.run must name each child <parent ID>/<depth>");
    }

    @Test
    @Tag("challenge2")
    void aFailureAtTheBottomIsWrappedOncePerLevel() {
        String root = "test-chain-" + shortId();
        ChainWorkflow workflow = stub(ChainWorkflow.class, root);

        WorkflowFailedException failed = assertThrows(WorkflowFailedException.class,
                () -> workflow.run(new ChainRequest(3, true)),
                "ChainWorkflowImpl.run must fail when the bottom of the chain fails");
        int wrappers = 0;
        Throwable cause = failed.getCause();
        while (cause instanceof ChildWorkflowFailure) {
            wrappers++;
            cause = cause.getCause();
        }
        assertEquals(3, wrappers, "a three-level chain must wrap the bottom failure three times");
        ApplicationFailure bottom = assertInstanceOf(ApplicationFailure.class, cause,
                "the bottom of the chain must fail with an ApplicationFailure");
        assertEquals("BottomOfChain", bottom.getType());
    }

    // --- Challenge 3: fan out -----------------------------------------------------

    @Test
    @Tag("challenge3")
    void fanOutStartsOneChildPerItem() {
        Order order = newOrder(ITEMS.toArray(String[]::new));
        String id = "test-pack-" + order.orderId();
        stub(PackWorkflow.class, id).pack(order);

        List<EventType> events = eventTypes(id);
        assertEquals(ITEMS.size(), count(events, EventType.EVENT_TYPE_START_CHILD_WORKFLOW_EXECUTION_INITIATED),
                "PackWorkflowImpl.pack must start one PackItemWorkflow child per item");
        assertEquals(ITEMS.size(), count(events, EventType.EVENT_TYPE_CHILD_WORKFLOW_EXECUTION_STARTED),
                "PackWorkflowImpl.pack must wait for every child to start");
    }

    // --- Challenge 4: fan in ------------------------------------------------------

    @Test
    @Tag("challenge4")
    void fanInReturnsEveryBoxInItemOrder() {
        Order order = newOrder(ITEMS.toArray(String[]::new));
        List<String> boxes = stub(PackWorkflow.class, "test-pack-" + order.orderId()).pack(order);

        assertEquals(List.of("box:mug", "box:book", "box:lamp"), boxes,
                "PackWorkflowImpl.pack returned " + boxes + ". Wait for every child with "
                        + "Promise.allOf(boxes), then return each box in item order (TODO 1)");
    }

    @Test
    @Tag("challenge4")
    void fanInWaitsForEveryChildRunningInParallel() {
        Order order = newOrder(ITEMS.toArray(String[]::new));
        String id = "test-pack-" + order.orderId();
        stub(PackWorkflow.class, id).pack(order);

        List<EventType> events = eventTypes(id);
        List<Integer> initiated = indexesOf(events, EventType.EVENT_TYPE_START_CHILD_WORKFLOW_EXECUTION_INITIATED);
        List<Integer> completed = indexesOf(events, EventType.EVENT_TYPE_CHILD_WORKFLOW_EXECUTION_COMPLETED);
        assertEquals(ITEMS.size(), completed.size(),
                "PackWorkflowImpl.pack returned before its children finished, so the default Parent Close "
                        + "Policy terminated them. Wait for every child with Promise.allOf(boxes) (TODO 1)");
        assertTrue(initiated.get(initiated.size() - 1) < completed.get(0),
                "PackWorkflowImpl.pack must start every child before waiting for any of them. "
                        + "Leave the fan-out loop as it is and wait only in TODO 1");
    }

    // --- helpers ------------------------------------------------------------------

    private <T> T stub(Class<T> type, String workflowId) {
        return client.newWorkflowStub(type, WorkflowOptions.newBuilder()
                .setTaskQueue(Shared.TASK_QUEUE)
                .setWorkflowId(workflowId)
                .setWorkflowExecutionTimeout(Duration.ofSeconds(30))
                .build());
    }

    private String parentOf(String workflowId) {
        return client.fetchHistory(workflowId).getEvents().get(0)
                .getWorkflowExecutionStartedEventAttributes().getParentWorkflowExecution().getWorkflowId();
    }

    private List<EventType> eventTypes(String workflowId) {
        return client.fetchHistory(workflowId).getEvents().stream().map(HistoryEvent::getEventType).toList();
    }

    private static long count(List<EventType> events, EventType type) {
        return events.stream().filter(type::equals).count();
    }

    private static List<Integer> indexesOf(List<EventType> events, EventType type) {
        List<Integer> at = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) == type) {
                at.add(i);
            }
        }
        return at;
    }

    private static Order newOrder(String... items) {
        return new Order("T-" + shortId(), List.of(items));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
