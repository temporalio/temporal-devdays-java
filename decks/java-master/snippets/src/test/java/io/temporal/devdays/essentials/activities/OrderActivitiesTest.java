package io.temporal.devdays.essentials.activities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 06-activities: the Activity on the anatomy slides calls a real HTTP Orders API (a local server
 * the test starts), and Temporal retries it when it throws.
 */
@Timeout(30)
class OrderActivitiesTest {
  private static final String QUEUE = "orders";
  private static final ObjectMapper JSON = new ObjectMapper();

  private TestWorkflowEnvironment env;
  private WorkflowClient client;
  private HttpServer ordersApi;
  private final Deque<Integer> statuses = new ConcurrentLinkedDeque<>();
  private final List<String> bodies = Collections.synchronizedList(new ArrayList<>());

  private final Order order = new Order("o-1", "sku-1", 2);

  @BeforeEach
  void setUp() throws IOException {
    ordersApi = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    ordersApi.createContext(
        "/submit",
        exchange -> {
          String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          bodies.add(body);
          Integer next = statuses.poll();
          int status = next == null ? 200 : next;
          byte[] out =
              status == 200
                  ? JSON.writeValueAsBytes(
                      new OrderResult(JSON.readValue(body, Order.class).orderId(), "submitted"))
                  : new byte[0];
          exchange.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
          if (out.length > 0) exchange.getResponseBody().write(out);
          exchange.close();
        });
    ordersApi.start();
    URI base = URI.create("http://127.0.0.1:" + ordersApi.getAddress().getPort());

    env = TestWorkflowEnvironment.newInstance();
    client = env.getWorkflowClient();
    Worker worker = env.newWorker(QUEUE);
    worker.registerWorkflowImplementationTypes(OrderWorkflow.Impl.class);
    worker.registerActivitiesImplementations(new OrderActivitiesImpl(base));
    env.start();
  }

  @AfterEach
  void tearDown() {
    env.close();
    ordersApi.stop(0);
  }

  private OrderWorkflow stub(String id) {
    return client.newWorkflowStub(
        OrderWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(QUEUE).setWorkflowId(id).build());
  }

  @Test
  void theWorkflowSchedulesTheActivityAndReturnsItsResult() throws IOException {
    OrderResult result = stub(UUID.randomUUID().toString()).submit(order);
    assertEquals(
        new OrderResult("o-1", "submitted"),
        result,
        "OrderActivitiesImpl.submitOrder should return the Orders API's answer");
    assertEquals(
        order,
        JSON.readValue(bodies.get(0), Order.class),
        "06-activities.md (Java Activity interface): the Order crosses into the Activity as JSON"
            + " and is posted as-is");
  }

  @Test
  void anActivityThatThrowsIsRetriedByThePlatform() {
    statuses.add(503);
    String id = UUID.randomUUID().toString();
    assertEquals(
        new OrderResult("o-1", "submitted"),
        stub(id).submit(order),
        "the 503 throws, Temporal retries, and the second attempt succeeds");
    assertEquals(2, bodies.size(), "the Orders API should have been called twice");
    List<HistoryEvent> started =
        client.fetchHistory(id).getEvents().stream()
            .filter(HistoryEvent::hasActivityTaskStartedEventAttributes)
            .toList();
    // One ActivityTaskStarted Event, recording the attempt that succeeded: the failed attempt
    // was retried inside the same scheduled Activity.
    assertEquals(1, started.size(), "a retried Activity writes one ActivityTaskStarted Event");
    assertEquals(
        2,
        started.get(0).getActivityTaskStartedEventAttributes().getAttempt(),
        "06-activities.md (Java Activity implementation): throwing triggers the retry; the"
            + " Started Event records attempt 2");
  }
}
