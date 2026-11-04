package io.temporal.devdays.essentials.history;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.devdays.essentials.deck.Slides;
import io.temporal.devdays.essentials.history.OutsideWorld.ConfigReadingWorkflow;
import io.temporal.devdays.essentials.history.OutsideWorld.ConfigReadingWorkflowImpl;
import io.temporal.devdays.essentials.history.Pizza.Bill;
import io.temporal.devdays.essentials.history.Pizza.Order;
import io.temporal.devdays.essentials.history.Pizza.PizzaActivitiesImpl;
import io.temporal.devdays.essentials.history.Pizza.PizzaWorkflow;
import io.temporal.devdays.essentials.history.Pizza.PizzaWorkflowImpl;
import io.temporal.devdays.essentials.history.ReplayAwareTools.Results;
import io.temporal.devdays.essentials.history.ReplayAwareTools.ToolsWorkflow;
import io.temporal.devdays.essentials.history.ReplayAwareTools.ToolsWorkflowImpl;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 06b-event-history: the pizza Workflow the ReplayFlow component steps through records the history
 * the component draws, replays without running its Activities again, and a Workflow that reads the
 * outside world fails to replay.
 */
@Timeout(30)
class EventHistoryTest {
  private static final String QUEUE = "history";

  private TestWorkflowEnvironment env;
  private WorkflowClient client;
  private final AtomicInteger mapsCalls = new AtomicInteger();

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    client = env.getWorkflowClient();
    Worker worker = env.newWorker(QUEUE);
    worker.registerWorkflowImplementationTypes(
        PizzaWorkflowImpl.class, ConfigReadingWorkflowImpl.class, ToolsWorkflowImpl.class);
    worker.registerActivitiesImplementations(
        new PizzaActivitiesImpl(
            (from, to) -> {
              mapsCalls.incrementAndGet();
              return 15;
            }),
        (OutsideWorld.Notifier) () -> {},
        (ReplayAwareTools.Pricing) sku -> sku.length());
    env.start();
  }

  @AfterEach
  void tearDown() {
    OutsideWorld.useTimer = false;
    env.close();
  }

  private <T> T stub(Class<T> type, String id) {
    return client.newWorkflowStub(
        type, WorkflowOptions.newBuilder().setTaskQueue(QUEUE).setWorkflowId(id).build());
  }

  /** EVENT_TYPE_ACTIVITY_TASK_SCHEDULED becomes ActivityTaskScheduled. */
  private static String name(HistoryEvent e) {
    return Arrays.stream(e.getEventType().name().replace("EVENT_TYPE_", "").split("_"))
        .map(w -> w.charAt(0) + w.substring(1).toLowerCase())
        .collect(Collectors.joining());
  }

  private static boolean isWorkflowTask(HistoryEvent e) {
    return e.getEventType().name().startsWith("EVENT_TYPE_WORKFLOW_TASK_");
  }

  private static final String REPLAY_FLOW =
      Slides.read(Slides.SNIPPETS.resolve("../components/ReplayFlow.vue").normalize());

  /** Every match of {@code item} in the {@code const NAME = [ ... ]} array of a component. */
  private static List<String> quoted(String source, String array, String item) {
    int start = source.indexOf(array);
    String block = source.substring(start, source.indexOf("\n]", start));
    List<String> out = new ArrayList<>();
    Matcher m = Pattern.compile(item).matcher(block);
    while (m.find()) out.add(m.group(1));
    return out;
  }

  @Test
  void replayFlowShowsThePizzaCodeLineForLine() {
    List<String> pizza =
        Slides.read(Slides.SRC.resolve("io/temporal/devdays/essentials/history/Pizza.java"))
            .lines()
            .map(String::strip)
            .toList();
    List<String> shown = new ArrayList<>(quoted(REPLAY_FLOW, "const CODE", "'([^']*)'"));
    shown.addAll(quoted(REPLAY_FLOW, "const ACTIVITY", "'([^']*)'"));
    assertFalse(shown.isEmpty(), "components/ReplayFlow.vue should have CODE and ACTIVITY lines");
    for (String line : shown) {
      assertTrue(
          pizza.contains(line.strip()),
          "components/ReplayFlow.vue shows \"" + line + "\" but Pizza.java has no such line");
    }
  }

  @Test
  void thePizzaWorkflowRecordsTheHistoryReplayFlowDraws() {
    String id = "pizza-" + UUID.randomUUID();
    Bill bill = stub(PizzaWorkflow.class, id).pizza(new Order("9 Elm St"));
    assertEquals(15, bill.distance(), "Pizza.java: getDistance returns 15, as ReplayFlow shows");

    List<HistoryEvent> events = client.fetchHistory(id).getEvents();
    List<String> drawn =
        events.stream().filter(e -> !isWorkflowTask(e)).map(EventHistoryTest::name).toList();
    assertEquals(
        quoted(REPLAY_FLOW, "const EVENTS", "name: '([^']+)'"),
        drawn,
        "components/ReplayFlow.vue's EVENTS should be the history Pizza.java records, Workflow"
            + " Task events left out");

    // The Activity Types ReplayFlow names are the ones the Event History records.
    List<String> recordedTypes =
        events.stream()
            .filter(e -> e.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED)
            .map(e -> e.getActivityTaskScheduledEventAttributes().getActivityType().getName())
            .toList();
    assertEquals(
        recordedTypes,
        quoted(REPLAY_FLOW, "const EVENTS", "name: 'ActivityTaskScheduled', detail: '([^']+)'"),
        "components/ReplayFlow.vue should name each Activity by the Activity Type the history"
            + " records (the method name, capitalised)");

    // WorkflowRun.vue and the notes: Started is written when the attempt finishes, so it sits
    // directly before Completed.
    for (int i = 0; i < events.size(); i++) {
      if (events.get(i).getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_STARTED) {
        assertEquals(
            EventType.EVENT_TYPE_ACTIVITY_TASK_COMPLETED,
            events.get(i + 1).getEventType(),
            "06b-event-history.md: ActivityTaskStarted is written beside ActivityTaskCompleted");
      }
    }
  }

  @Test
  void replayReadsActivityResultsBackInsteadOfRunningThemAgain() {
    String id = "pizza-" + UUID.randomUUID();
    stub(PizzaWorkflow.class, id).pizza(new Order("9 Elm St"));
    assertEquals(1, mapsCalls.get(), "getDistance ran once on the first run");

    WorkflowExecutionHistory history = client.fetchHistory(id);
    assertDoesNotThrow(
        () -> WorkflowReplayer.replayWorkflowExecution(history, PizzaWorkflowImpl.class),
        "06b-event-history.md (Event History and Replay): the same code replays the same history");
    assertEquals(
        1,
        mapsCalls.get(),
        "06b-event-history.md (Event History and Replay): replay must not call getDistance again");
  }

  @Test
  void readingTheOutsideWorldBreaksReplay() {
    String id = "config-" + UUID.randomUUID();
    stub(ConfigReadingWorkflow.class, id).run();
    WorkflowExecutionHistory history = client.fetchHistory(id);

    OutsideWorld.useTimer = true; // the "config file" changed between the run and the replay
    Exception e =
        assertThrows(
            Exception.class,
            () -> WorkflowReplayer.replayWorkflowExecution(history, ConfigReadingWorkflowImpl.class),
            "06b-event-history.md: replay produces StartTimer where the history says"
                + " ScheduleActivityTask, a non-determinism error");
    String chain = "";
    for (Throwable t = e; t != null; t = t.getCause()) {
      chain += t.getClass().getName() + ": " + t.getMessage() + "\n";
    }
    assertTrue(
        chain.contains("NonDeterministicException"),
        "06b-event-history.md: the replay should fail with a non-determinism error, got " + chain);
  }

  @Test
  void theReplayAwareMethodsDoWhatTheTableSays() {
    String id = "tools-" + UUID.randomUUID();
    Results r = stub(ToolsWorkflow.class, id).run(List.of("a", "bb", "ccc"));
    assertEquals(6, r.total(), "Async.function + Promise.allOf: all three prices, 1 + 2 + 3");
    assertEquals("quick", r.firstToFinish(), "Promise.anyOf: the one-minute timer wins");
    assertFalse(r.signalArrived(), "Workflow.await with a timeout returns false on timeout");

    WorkflowExecutionHistory history = client.fetchHistory(id);
    assertDoesNotThrow(
        () -> WorkflowReplayer.replayWorkflowExecution(history, ToolsWorkflowImpl.class),
        "06b-event-history.md (More replay-aware methods): ReplayAwareTools.java uses only"
            + " replay-safe calls, so its history replays without a non-determinism error");
  }
}
