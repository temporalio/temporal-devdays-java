package io.temporal.devdays.essentials.encryption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.api.common.v1.Payload;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The encryption segment's claims: plaintext for codec holders, ciphertext for everyone else. */
class EncryptionTest {
  private TestWorkflowEnvironment env;
  private EncryptionSetup.Wired wired;

  @BeforeEach
  void start() {
    env = TestWorkflowEnvironment.newInstance();
    wired =
        EncryptionSetup.withCodec(env.getWorkflowServiceStubs(), AesGcm.generateKey(), "greet");
    wired.worker().registerWorkflowImplementationTypes(GreetWorkflowImpl.class);
    wired.factory().start();
  }

  @AfterEach
  void stop() {
    wired.factory().shutdownNow();
    env.close();
  }

  private String runGreet(String workflowId) {
    return wired
        .client()
        .newWorkflowStub(
            GreetWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("greet").setWorkflowId(workflowId).build())
        .greet("Ada Lovelace");
  }

  @Test
  void aClientWithTheCodecReadsPlaintextAndTheWorkflowIsUnchanged() {
    assertEquals(
        "Hello, Ada Lovelace",
        runGreet("encryption-" + UUID.randomUUID()),
        "EncryptionSetup.java (\"Same codec, Client and Worker\"): a Client with the codec should"
            + " read the plaintext result of a Workflow that knows nothing about encryption");
  }

  @Test
  void theServerStoresOnlyCiphertext() {
    String workflowId = "encryption-" + UUID.randomUUID();
    runGreet(workflowId);
    // env.getWorkflowClient() has no codec, so it sees what the Server stores.
    HistoryEvent started =
        env.getWorkflowClient().fetchHistory(workflowId).getEvents().stream()
            .filter(HistoryEvent::hasWorkflowExecutionStartedEventAttributes)
            .findFirst()
            .orElseThrow();
    Payload input = started.getWorkflowExecutionStartedEventAttributes().getInput().getPayloads(0);
    assertEquals(
        "binary/encrypted",
        input.getMetadataOrThrow("encoding").toStringUtf8(),
        "EncryptionCodec.java (\"A codec in practice\"): the stored Payload should be labelled"
            + " binary/encrypted");
    assertFalse(
        new String(input.getData().toByteArray(), StandardCharsets.ISO_8859_1).contains("Ada"),
        "EncryptionCodec.java: the stored Payload should not contain the plaintext");
  }

  @Test
  void aClientWithoutTheCodecCannotReadTheResult() {
    String workflowId = "encryption-" + UUID.randomUUID();
    runGreet(workflowId);
    Exception e =
        assertThrows(
            Exception.class,
            () -> env.getWorkflowClient().newUntypedWorkflowStub(workflowId).getResult(String.class),
            "the \"Same codec\" notes claim a Client without the codec cannot read the result");
    StringBuilder all = new StringBuilder();
    for (Throwable c = e; c != null; c = c.getCause()) all.append(c).append(" | ");
    assertTrue(
        all.toString().contains("No PayloadConverter is registered for an encoding: binary/encrypted"),
        "the \"Same codec\" notes quote the missing-codec error; got " + all);
  }
}
