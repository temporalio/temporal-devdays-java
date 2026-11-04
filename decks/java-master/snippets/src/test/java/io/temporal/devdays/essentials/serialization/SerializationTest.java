package io.temporal.devdays.essentials.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.api.common.v1.Payload;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.converter.DataConverterException;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The serialization segment's claims about the default Jackson converter and a custom one. */
class SerializationTest {
  private static final ZonedDateTime PARIS_NINE =
      ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneId.of("Europe/Paris"));

  private TestWorkflowEnvironment env;

  @BeforeEach
  void start() {
    env = TestWorkflowEnvironment.newInstance();
  }

  @AfterEach
  void stop() {
    env.close();
  }

  private <T> T stub(WorkflowClient client, Class<T> type, String taskQueue) {
    return client.newWorkflowStub(
        type,
        WorkflowOptions.newBuilder()
            .setTaskQueue(taskQueue)
            .setWorkflowId("serialization-" + UUID.randomUUID())
            .build());
  }

  @Test
  void aRecordRoundTripsThroughAWorkflowAndAnActivity() {
    Worker worker = env.newWorker("claims");
    worker.registerWorkflowImplementationTypes(ClaimWorkflowImpl.class);
    worker.registerActivitiesImplementations(new ClaimActivitiesImpl());
    env.start();
    ClaimResult result =
        stub(env.getWorkflowClient(), ClaimWorkflow.class, "claims")
            .process(new Claim("p-1", "0002-1433-80", 12_500L));
    assertEquals(
        new ClaimResult("p-1", 12_500L),
        result,
        "Claim.java and ClaimWorkflowImpl.java (\"Data serialization\" slide): a record should"
            + " make the round trip with no annotations");
  }

  @Test
  void theDefaultConverterDropsTheZoneIdAndLoosensObjectTypedValues() {
    Worker worker = env.newWorker("echo");
    worker.registerWorkflowImplementationTypes(EchoWorkflowImpl.class);
    env.start();
    Visit received = JsonTraps.roundTrip(stub(env.getWorkflowClient(), EchoWorkflow.class, "echo"));

    assertEquals(
        ZoneOffset.ofHours(1),
        received.at().getZone(),
        "JsonTraps.java (\"What JSON does to your values\"): Europe/Paris should come back as a"
            + " bare +01:00 offset");
    assertEquals(
        PARIS_NINE.toInstant(),
        received.at().toInstant(),
        "JsonTraps.java: the instant itself should survive; only the zone ID is lost");
    assertTrue(
        !received.at().equals(PARIS_NINE),
        "JsonTraps.java: the received ZonedDateTime should no longer equal the one sent");
    assertInstanceOf(
        Integer.class,
        received.extras().get("copayCents"),
        "JsonTraps.java: a Long in a Map<String, Object> should come back an Integer");
    assertEquals(1_500, received.extras().get("copayCents"));
    assertEquals(
        "2026-01-01T09:30:00Z",
        received.extras().get("seenAt"),
        "JsonTraps.java: an Instant in a Map<String, Object> should come back a String");
  }

  sealed interface Payment permits Card, Cash {}

  record Card(String last4) implements Payment {}

  record Cash(long cents) implements Payment {}

  @Test
  void anInterfaceTypedValueCannotBeReadBack() {
    Payload payload =
        DefaultDataConverter.STANDARD_INSTANCE.toPayload(new Card("4242")).orElseThrow();
    DataConverterException e =
        assertThrows(
            DataConverterException.class,
            () -> DefaultDataConverter.STANDARD_INSTANCE.fromPayload(payload, Payment.class, Payment.class),
            "the \"What JSON does\" notes claim a value declared as an interface cannot be read"
                + " back without type information");
    assertTrue(
        e.getMessage().contains("abstract types"),
        "the notes quote Jackson's \"abstract types\" error; got " + e.getMessage());
  }

  @Test
  void aNestedObjectInAnObjectTypedSlotComesBackAMap() {
    Visit sent = new Visit(PARIS_NINE, Map.of("card", new Card("4242")));
    Payload payload = DefaultDataConverter.STANDARD_INSTANCE.toPayload(sent).orElseThrow();
    Visit received =
        DefaultDataConverter.STANDARD_INSTANCE.fromPayload(payload, Visit.class, Visit.class);
    assertInstanceOf(
        java.util.LinkedHashMap.class,
        received.extras().get("card"),
        "the \"What JSON does\" notes claim a nested object in Map<String, Object> comes back a"
            + " LinkedHashMap");
  }

  @Test
  void theCustomConverterKeepsTheZoneInTheWorkflowAndBackAtTheClient() {
    SerializationSetup.Wired wired =
        SerializationSetup.withConverter(
            env.getWorkflowServiceStubs(), SerializationSetup.dataConverter(), "zoned");
    wired.worker().registerWorkflowImplementationTypes(InspectVisitWorkflowImpl.class);
    wired.factory().start();
    try {
      VisitCheck check =
          stub(wired.client(), InspectVisitWorkflow.class, "zoned")
              .inspect(new Visit(PARIS_NINE, Map.of()));
      assertEquals(
          "Europe/Paris",
          check.zone(),
          "ZonedJsonConverter.java (\"A custom payload converter\"): the Workflow should receive"
              + " the zone ID");
      assertEquals(
          PARIS_NINE,
          check.visit().at(),
          "SerializationSetup.java (\"Register it on both sides\"): the Client should read the"
              + " zone ID back");
    } finally {
      wired.factory().shutdownNow();
    }
  }

  @Test
  void aClientWithoutTheConverterCannotReadTheResult() {
    SerializationSetup.Wired wired =
        SerializationSetup.withConverter(
            env.getWorkflowServiceStubs(), SerializationSetup.dataConverter(), "zoned");
    wired.worker().registerWorkflowImplementationTypes(InspectVisitWorkflowImpl.class);
    wired.factory().start();
    try {
      InspectVisitWorkflow wf = stub(wired.client(), InspectVisitWorkflow.class, "zoned");
      wf.inspect(new Visit(PARIS_NINE, Map.of()));
      String workflowId = io.temporal.client.WorkflowStub.fromTyped(wf).getExecution().getWorkflowId();
      // env.getWorkflowClient() has the default converter only.
      Exception e =
          assertThrows(
              Exception.class,
              () ->
                  env.getWorkflowClient()
                      .newUntypedWorkflowStub(workflowId)
                      .getResult(VisitCheck.class),
              "the \"Register it on both sides\" notes claim a Client without the converter cannot"
                  + " read the result");
      StringBuilder all = new StringBuilder();
      for (Throwable c = e; c != null; c = c.getCause()) all.append(c).append(" | ");
      assertTrue(
          all.toString().contains("No PayloadConverter is registered for an encoding: json/zoned"),
          "the \"Register it on both sides\" notes quote the missing-converter error; got " + all);
    } finally {
      wired.factory().shutdownNow();
    }
  }
}
