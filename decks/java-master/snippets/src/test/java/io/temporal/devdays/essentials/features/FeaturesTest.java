package io.temporal.devdays.essentials.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.temporal.api.common.v1.Priority;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.ScheduleActionExecutionStartWorkflow;
import io.temporal.client.schedules.ScheduleActionResult;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleClientOptions;
import io.temporal.client.schedules.ScheduleDescription;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.testing.TemporalDevServerOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.io.File;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 22-more-features.md: the Schedule and the Priority options do what the slides say.
 *
 * <p>The in-memory test server has no Schedules, so this runs on a dev server: the {@code temporal}
 * CLI on the PATH if there is one, otherwise the one TestWorkflowEnvironment downloads.
 */
class FeaturesTest {
  private static TestWorkflowEnvironment env;
  private static WorkflowClient client;

  @BeforeAll
  static void startDevServer() {
    TemporalDevServerOptions.Builder server = TemporalDevServerOptions.newBuilder();
    String cli = onPath("temporal");
    if (cli != null) server.setExistingPath(cli);
    env = TestWorkflowEnvironment.startLocal(server.build());
    client = env.getWorkflowClient();
    Worker reports = env.newWorker("reports");
    reports.registerWorkflowImplementationTypes(ReportWorkflowImpl.class);
    reports.registerActivitiesImplementations(new ReportActivitiesImpl());
    Worker orders = env.newWorker("orders");
    orders.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
    orders.registerActivitiesImplementations(new OrderActivitiesImpl());
    env.start();
  }

  @AfterAll
  static void stopDevServer() {
    if (env != null) env.close();
  }

  private static String onPath(String name) {
    for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
      File f = new File(dir, name);
      if (f.canExecute()) return f.getAbsolutePath();
    }
    return null;
  }

  @Test
  void theScheduleLivesOnTheServiceAndEachRunIsAWorkflow() throws InterruptedException {
    ScheduleClient scheduleClient =
        ScheduleClient.newInstance(
            env.getWorkflowServiceStubs(),
            ScheduleClientOptions.newBuilder().setNamespace(env.getNamespace()).build());
    ScheduleHandle schedule = DailyReportSchedule.create(scheduleClient);
    try {
      ScheduleDescription described = schedule.describe();
      assertEquals(
          "daily-report",
          described.getId(),
          "DailyReportSchedule.java: the slide says the Schedule lives under its own ID");
      assertEquals(
          ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP,
          described.getSchedule().getPolicy().getOverlap(),
          "DailyReportSchedule.java: the slide's overlap policy is SKIP");
      assertEquals(
          9,
          described.getInfo().getNextActionTimes().get(0).atZone(ZoneOffset.UTC).getHour(),
          "DailyReportSchedule.java: \"0 9 * * *\" should next fire at 09:00 UTC");

      schedule.trigger();
      String workflowId = null;
      for (int i = 0; i < 80 && workflowId == null; i++) {
        List<ScheduleActionResult> recent = schedule.describe().getInfo().getRecentActions();
        if (!recent.isEmpty()) {
          workflowId =
              ((ScheduleActionExecutionStartWorkflow) recent.get(0).getAction()).getWorkflowId();
        } else {
          Thread.sleep(250);
        }
      }
      assertNotNull(workflowId, "DailyReportSchedule.java: trigger() never started a Workflow");
      assertEquals(
          "report ready",
          client.newUntypedWorkflowStub(workflowId).getResult(String.class),
          "DailyReportSchedule.java: each run should be an ordinary Workflow Execution");
    } finally {
      schedule.delete();
    }
  }

  @Test
  void theActivityIsScheduledWithThePriorityAndFairnessFromTheSlide() {
    String workflowId = "order-" + UUID.randomUUID();
    OrderWorkflow workflow =
        client.newWorkflowStub(
            OrderWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("orders").setWorkflowId(workflowId).build());
    assertEquals(
        "charged A-1 for tenant-a",
        workflow.processOrder(new Order("A-1", "tenant-a")),
        "OrderWorkflowImpl.java: the Workflow should charge the order");

    HistoryEvent scheduled =
        client.fetchHistory(workflowId).getEvents().stream()
            .filter(HistoryEvent::hasActivityTaskScheduledEventAttributes)
            .findFirst()
            .orElseThrow();
    Priority priority = scheduled.getActivityTaskScheduledEventAttributes().getPriority();
    assertEquals(1, priority.getPriorityKey(), "OrderWorkflowImpl.java: setPriorityKey(1)");
    assertEquals(
        "tenant-a",
        priority.getFairnessKey(),
        "OrderWorkflowImpl.java: setFairnessKey(order.tenantId())");
    assertEquals(2f, priority.getFairnessWeight(), "OrderWorkflowImpl.java: setFairnessWeight(2)");
  }
}
