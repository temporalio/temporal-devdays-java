package io.temporal.devdays.essentials.versioning;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.api.enums.v1.VersioningBehavior;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.api.workflow.v1.WorkflowExecutionVersioningInfo;
import io.temporal.api.workflowservice.v1.SetWorkerDeploymentCurrentVersionRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.testing.TemporalDevServerOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.NonDeterministicException;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import java.io.File;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * The versioning segment's claims, on the in-memory test server: what an unversioned edit does to
 * replay, how getVersion keeps old runs on the old branch, and how the patch is retired.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VersioningTest {
  private static final List<Class<? extends MyWorkflow>> IMPLS =
      List.of(
          BeforeWorkflowImpl.class,
          MyWorkflowImpl.class,
          RetiredWorkflowImpl.class,
          FinalWorkflowImpl.class);

  private TestWorkflowEnvironment env;
  private String beforeRunId; // started on the original code: A, B
  private String patchedRunId; // started after the patch: A, marker, C

  @BeforeAll
  void startRuns() {
    env = TestWorkflowEnvironment.newInstance();
    for (Class<? extends MyWorkflow> impl : IMPLS) {
      Worker worker = env.newWorker(impl.getSimpleName());
      worker.registerWorkflowImplementationTypes(impl);
      worker.registerActivitiesImplementations(new VersioningActivitiesImpl());
    }
    env.start();
    beforeRunId = run(BeforeWorkflowImpl.class);
    patchedRunId = run(MyWorkflowImpl.class);
  }

  @AfterAll
  void stop() {
    env.close();
  }

  private String run(Class<? extends MyWorkflow> impl) {
    String workflowId = "versioning-" + UUID.randomUUID();
    MyWorkflow wf =
        env.getWorkflowClient()
            .newWorkflowStub(
                MyWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(impl.getSimpleName())
                    .setWorkflowId(workflowId)
                    .build());
    wf.run();
    return workflowId;
  }

  private List<HistoryEvent> events(String workflowId) {
    return env.getWorkflowClient().fetchHistory(workflowId).getEvents();
  }

  private List<String> activitiesRun(String workflowId) {
    return events(workflowId).stream()
        .filter(HistoryEvent::hasActivityTaskScheduledEventAttributes)
        .map(e -> e.getActivityTaskScheduledEventAttributes().getActivityType().getName())
        .toList();
  }

  private void replay(String workflowId, Class<?> impl) throws Exception {
    WorkflowExecutionHistory history = env.getWorkflowClient().fetchHistory(workflowId);
    WorkflowReplayer.replayWorkflowExecution(history, impl);
  }

  /** The replayer reports a non-determinism error as a query failure that names it. */
  private static boolean nonDeterministic(Throwable t) {
    return chain(t).contains(NonDeterministicException.class.getName());
  }

  private void assertReplayFails(String workflowId, Class<?> impl, String claim) {
    Exception e = assertThrows(Exception.class, () -> replay(workflowId, impl), claim);
    assertTrue(nonDeterministic(e), claim + ", with a non-determinism error; got " + e);
  }

  @Test
  void theOriginalCodeRunsAThenB() {
    assertEquals(
        List.of("ActivityA", "ActivityB"),
        activitiesRun(beforeRunId),
        "BeforeWorkflowImpl.java (\"Before\" slide) should schedule A, then B");
  }

  @Test
  void swappingBForCWithoutAPatchBreaksReplayOfAnOldRun() {
    assertReplayFails(
        beforeRunId,
        FinalWorkflowImpl.class,
        "the \"Before\" slide notes claim that editing B to C breaks replay of a run that ran B");
  }

  @Test
  void thePatchedCodeReplaysAnOldRunDownTheOldBranch() {
    assertDoesNotThrow(
        () -> VersioningReplay.replay(env.getWorkflowClient(), beforeRunId),
        "MyWorkflowImpl.java (\"After\" slide) should replay a run that started before the patch");
  }

  @Test
  void aNewRunTakesTheNewBranchAndRecordsAVersionMarker() {
    assertEquals(
        List.of("ActivityA", "ActivityC"),
        activitiesRun(patchedRunId),
        "MyWorkflowImpl.java (\"After\" slide): a new execution should take the C branch");
    List<String> markers =
        events(patchedRunId).stream()
            .filter(HistoryEvent::hasMarkerRecordedEventAttributes)
            .map(e -> e.getMarkerRecordedEventAttributes().getMarkerName())
            .toList();
    assertEquals(
        List.of("Version"),
        markers,
        "MyWorkflowImpl.java: getVersion should record one Version marker in the Event History");
  }

  @Test
  void deletingGetVersionOutrightStillReplaysARunThatRecordedTheMarker() {
    assertDoesNotThrow(
        () -> replay(patchedRunId, FinalWorkflowImpl.class),
        "the \"Retiring the patch\" notes claim Java skips a Version marker with no getVersion call");
  }

  @Test
  void theRetiredPatchReplaysRunsThatHaveTheMarker() {
    assertDoesNotThrow(
        () -> replay(patchedRunId, RetiredWorkflowImpl.class),
        "RetiredWorkflowImpl.java (\"Retiring the patch\") should replay a run that took version 1");
  }

  @Test
  void theRetiredPatchRefusesARunStillOnTheOldBranch() {
    Exception e =
        assertThrows(
            Exception.class,
            () -> replay(beforeRunId, RetiredWorkflowImpl.class),
            "RetiredWorkflowImpl.java (\"Retiring the patch\") should refuse a run that never"
                + " recorded the marker");
    String all = chain(e);
    assertTrue(
        all.contains("use-activity-c") && all.contains("not supported"),
        "the \"Retiring the patch\" notes quote getVersion's unsupported-version error; got " + all);
  }

  @Test
  void aRunStartedOnTheRetiredCodeReplaysOnceTheCallIsRemoved() {
    String retiredRunId = run(RetiredWorkflowImpl.class);
    assertDoesNotThrow(
        () -> replay(retiredRunId, FinalWorkflowImpl.class),
        "the \"Retiring the patch\" notes: step three, deleting getVersion, is safe for these runs");
  }

  @Test
  void workerVersioningRequiresABehaviour() {
    Worker worker =
        WorkerFactory.newInstance(env.getWorkflowClient())
            .newWorker(
                "unversioned-behaviour",
                io.temporal.worker.WorkerOptions.newBuilder()
                    .setDeploymentOptions(
                        io.temporal.worker.WorkerDeploymentOptions.newBuilder()
                            .setUseVersioning(true)
                            .setVersion(
                                new io.temporal.common.WorkerDeploymentVersion("orders", "2.0.0"))
                            .build())
                    .build());
    assertThrows(
        IllegalArgumentException.class,
        () -> worker.registerWorkflowImplementationTypes(MyWorkflowImpl.class),
        "the \"Worker Versioning\" notes claim registering a Workflow with no versioning behaviour"
            + " throws when versioning is on");
  }

  private static String chain(Throwable t) {
    StringBuilder sb = new StringBuilder();
    for (Throwable c = t; c != null; c = c.getCause()) sb.append(c).append(" | ");
    return sb.toString();
  }

  /**
   * Worker Versioning needs Worker Deployments, which the in-memory test server does not have, so
   * this test starts a local dev server: the `temporal` CLI on the PATH, or a downloaded one.
   */
  @Test
  void workerVersioningPinsANewRunToTheCurrentVersion() throws Exception {
    TemporalDevServerOptions.Builder server = TemporalDevServerOptions.newBuilder();
    String cli = temporalOnPath();
    if (cli != null) server.setExistingPath(cli);
    try (TestWorkflowEnvironment local = TestWorkflowEnvironment.startLocal(server.build())) {
      WorkflowClient client = local.getWorkflowClient();
      WorkerFactory factory = WorkerFactory.newInstance(client);
      VersionedWorker.create(factory);
      factory.start();
      try {
        // The version exists once its Worker has polled; until then this call fails.
        for (int i = 0; ; i++) {
          try {
            local
                .getWorkflowServiceStubs()
                .blockingStub()
                .setWorkerDeploymentCurrentVersion(
                    SetWorkerDeploymentCurrentVersionRequest.newBuilder()
                        .setNamespace(local.getNamespace())
                        .setDeploymentName("orders")
                        .setBuildId("2.0.0")
                        .build());
            break;
          } catch (RuntimeException e) {
            if (i > 100) throw e;
            Thread.sleep(200);
          }
        }
        String workflowId = "versioning-" + UUID.randomUUID();
        MyWorkflow wf =
            client.newWorkflowStub(
                MyWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue("orders").setWorkflowId(workflowId).build());
        wf.run();
        WorkflowExecutionVersioningInfo info =
            WorkflowStub.fromTyped(wf)
                .describe()
                .getRawDescription()
                .getWorkflowExecutionInfo()
                .getVersioningInfo();
        assertEquals(
            VersioningBehavior.VERSIONING_BEHAVIOR_PINNED,
            info.getBehavior(),
            "VersionedWorker.java (\"Worker Versioning\" slide) should pin new executions");
        assertEquals(
            "2.0.0",
            info.getDeploymentVersion().getBuildId(),
            "VersionedWorker.java: the execution should be pinned to build 2.0.0");
      } finally {
        factory.shutdownNow();
      }
    }
  }

  private static String temporalOnPath() {
    for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
      File f = new File(dir, "temporal");
      if (f.canExecute()) return f.getAbsolutePath();
    }
    return null;
  }
}
