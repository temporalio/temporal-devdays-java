package io.temporal.devdays.essentials.claimcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import io.temporal.api.common.v1.Payload;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The claim-check segment's claims: only a key enters the Event History, by hand or by the SDK. */
class ClaimCheckTest {
  private TestWorkflowEnvironment env;

  @BeforeEach
  void start() {
    env = TestWorkflowEnvironment.newInstance();
  }

  @AfterEach
  void stop() {
    env.close();
  }

  /** Every Payload anywhere in a history, as a list of byte sizes. */
  static List<Integer> payloadSizes(List<HistoryEvent> events) {
    List<Integer> sizes = new ArrayList<>();
    events.forEach(e -> walk(e, sizes));
    return sizes;
  }

  private static void walk(Message message, List<Integer> sizes) {
    if (message instanceof Payload p) {
      sizes.add(p.getData().size());
      return;
    }
    for (Map.Entry<FieldDescriptor, Object> field : message.getAllFields().entrySet()) {
      Object value = field.getValue();
      if (value instanceof Message m) {
        walk(m, sizes);
      } else if (value instanceof List<?> list) {
        for (Object item : list) {
          if (item instanceof Message m) walk(m, sizes);
        }
      }
    }
  }

  private int largestPayload(String workflowId) {
    return payloadSizes(env.getWorkflowClient().fetchHistory(workflowId).getEvents()).stream()
        .mapToInt(Integer::intValue)
        .max()
        .orElse(0);
  }

  @Test
  void movesThreeMegabytesThroughTheWorkflowWithOnlyAKeyInItsEventHistory() {
    InMemoryBlobStore blobs = new InMemoryBlobStore();
    Worker worker = env.newWorker("documents");
    worker.registerWorkflowImplementationTypes(DocumentWorkflowImpl.class);
    worker.registerActivitiesImplementations(
        new DocumentActivitiesImpl(new ClaimCheckStorage(blobs)));
    env.start();
    String workflowId = "claim-check-" + UUID.randomUUID();
    String summary =
        env.getWorkflowClient()
            .newWorkflowStub(
                DocumentWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue("documents")
                    .setWorkflowId(workflowId)
                    .build())
            .process("https://example.com/report.pdf");
    assertEquals(
        DocumentActivitiesImpl.DOCUMENT_BYTES + " bytes",
        summary,
        "DocumentWorkflowImpl.java (\"The Workflow holds only the key\"): summarizeDocument should"
            + " fetch all 3 MB by key");
    int largest = largestPayload(workflowId);
    assertTrue(
        largest < 1024,
        "DocumentWorkflowImpl.java: no payload in the Event History should be over 1 KB; largest "
            + largest);
    assertEquals(
        0,
        blobs.size(),
        "DocumentWorkflowImpl.java: the finally block should delete the object on the way out");
  }

  @Test
  void aMissingKeyFailsForGood() {
    ClaimCheckStorage storage = new ClaimCheckStorage(new InMemoryBlobStore());
    ApplicationFailure failure =
        assertThrows(
            ApplicationFailure.class,
            () -> storage.fetchFromStorage("no-such-key"),
            "ClaimCheckStorage.java (\"Fetch by key\"): a missing key should throw");
    assertTrue(
        failure.isNonRetryable(),
        "ClaimCheckStorage.java (\"Fetch by key\"): a missing key should be non-retryable");
    assertEquals("NoSuchKey", failure.getType());
  }

  @io.temporal.workflow.WorkflowInterface
  public interface BigResultWorkflow {
    @io.temporal.workflow.WorkflowMethod
    byte[] build();
  }

  public static class BigResultWorkflowImpl implements BigResultWorkflow {
    @Override
    public byte[] build() {
      return new byte[ReportActivitiesImpl.REPORT_BYTES];
    }
  }

  @Test
  void aClientWithoutExternalStorageCannotReadAnOffloadedResult() {
    InMemoryDriver driver = new InMemoryDriver();
    ExternalStorageSetup.Wired wired =
        ExternalStorageSetup.withExternalStorage(env.getWorkflowServiceStubs(), driver, "big");
    wired.worker().registerWorkflowImplementationTypes(BigResultWorkflowImpl.class);
    wired.factory().start();
    try {
      String workflowId = "claim-check-" + UUID.randomUUID();
      byte[] result =
          wired
              .client()
              .newWorkflowStub(
                  BigResultWorkflow.class,
                  WorkflowOptions.newBuilder().setTaskQueue("big").setWorkflowId(workflowId).build())
              .build();
      assertEquals(ReportActivitiesImpl.REPORT_BYTES, result.length);
      // env.getWorkflowClient() has no External Storage configured.
      assertThrows(
          Exception.class,
          () -> env.getWorkflowClient().newUntypedWorkflowStub(workflowId).getResult(byte[].class),
          "the \"Or let the SDK do it\" notes claim a Client without External Storage cannot read"
              + " an offloaded result");
    } finally {
      wired.factory().shutdownNow();
    }
  }

  @Test
  void externalStorageOffloadsAOneMegabyteActivityResult() {
    InMemoryDriver driver = new InMemoryDriver();
    ExternalStorageSetup.Wired wired =
        ExternalStorageSetup.withExternalStorage(env.getWorkflowServiceStubs(), driver, "reports");
    wired.worker().registerWorkflowImplementationTypes(ReportWorkflowImpl.class);
    wired.worker().registerActivitiesImplementations(new ReportActivitiesImpl());
    wired.factory().start();
    try {
      String workflowId = "claim-check-" + UUID.randomUUID();
      int length =
          wired
              .client()
              .newWorkflowStub(
                  ReportWorkflow.class,
                  WorkflowOptions.newBuilder()
                      .setTaskQueue("reports")
                      .setWorkflowId(workflowId)
                      .build())
              .reportLength();
      assertEquals(
          ReportActivitiesImpl.REPORT_BYTES,
          length,
          "ExternalStorageSetup.java (\"Or let the SDK do it\"): the Workflow should see the full"
              + " 1 MB value");
      assertEquals(
          1,
          driver.stored.size(),
          "ExternalStorageSetup.java: the 1 MB Activity result should be offloaded to the driver");
      int largest = largestPayload(workflowId);
      assertTrue(
          largest < 1024,
          "ExternalStorageSetup.java: only a small reference should be in the Event History;"
              + " largest "
              + largest);
    } finally {
      wired.factory().shutdownNow();
    }
  }
}
