---
layout: section
---

<ZiggyHero scene="pack">

# Data Serialization

Everything that crosses the boundary gets converted.

</ZiggyHero>

<!--
Source: PPT slides 59-60, `{{BLOCK:module-serialization-demo}}`.

The original slide leaned on serialization attributes. Java needs none: the
default data converter is Jackson, and a plain record is enough. That is the
point of this segment, and also where its traps come from.
-->

---
layout: code-walkthrough
heading: Data serialization
notes:
  - A record. No annotation, no base class, no <code class="ref">Serializable</code>
  - Workflow arguments are serialized by the Client and deserialized by the Worker
  - Activity arguments and results make the same trip
  - So does the Workflow result. Every value here makes a round trip through JSON
---

::code::

```java {all|1|4|6|8-9}
public record Claim(String patientId, String drugNdc, long billedAmountCents) {}

@Override
public ClaimResult process(Claim claim) {
  // `claim` was JSON on the wire a moment ago
  ClaimResult result = activities.processClaim(claim);

  // and this crosses the wire on the way back out
  return result;
}
```

<!--
Source: PPT slide 60.

Code: snippets/src/main/java/io/temporal/devdays/essentials/serialization/Claim.java
and ClaimWorkflowImpl.java

The default data converter turns values into JSON with Jackson
(`JacksonJsonPayloadConverter`). In Java that means the declared type does a
lot of the work for you: Jackson reads the JSON back into exactly the type the
method signature names. The traps are where the declared type says too little
(next slide).

- **Build 1 - the shape.** A record works as is: no annotation, no no-arg
  constructor, no `Serializable`. A plain class works too; the SDK's
  `ObjectMapper` reads fields directly, private ones included.
- **Build 2 - arguments in.** Serialized by the Client, deserialized by the
  Worker, and they may be different processes on different machines.
- **Build 3 - Activity calls.** `processClaim` is an Activity: its argument is
  serialized by the Workflow, stored in the Event History, and handed to
  whichever Worker picks up the Activity Task. Its result comes back the same
  way.
- **Build 4 - results out.** Same trip in reverse, to whoever is waiting on the
  Workflow.

The default converter tries, in order: `null`, `byte[]` (as raw bytes),
protobuf messages, and then Jackson JSON for everything else. The SDK's
`ObjectMapper` already registers the `java.time` and `Optional` modules, and
writes dates as ISO strings.

Payload size matters: every argument and result is stored in the Event
History, and Temporal rejects a single payload over 2 MB by default. That is
the problem the Claim-Check segment already solved.
-->

---
layout: code-walkthrough
heading: What JSON does to your values
notes:
  - A ZonedDateTime and a loosely typed map go in
  - "<code>Europe/Paris</code> is gone. The time comes back with a bare offset"
  - "<code>copayCents</code> went in a Long and comes back an Integer"
  - "<code>seenAt</code> went in an Instant and comes back a String"
---

::code::

```java {all|3-7|4|6|7}
Visit received =
    echo.echo(
        new Visit(
            ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneId.of("Europe/Paris")),
            Map.of(
                "copayCents", 1_500L,
                "seenAt", Instant.parse("2026-01-01T09:30:00Z"))));

received.at(); // 2026-01-01T09:00+01:00: the zone ID is gone
received.extras().get("copayCents"); // 1500: an Integer now, not a Long
received.extras().get("seenAt"); // "2026-01-01T09:30:00Z": a String
```

<!--
`echo` is a Workflow that returns its argument, and `Visit` is
`record Visit(ZonedDateTime at, Map<String, Object> extras)`. The tests behind
this Deck assert each of the three results in the comments.

Code: snippets/src/main/java/io/temporal/devdays/essentials/serialization/JsonTraps.java

- **Build 1 - the input.** Ordinary values, all legal for the `Visit` type the
  Workflow declares.
- **Build 2 - the zone.** The SDK's `ObjectMapper` writes a `ZonedDateTime` as
  an ISO string with its offset, not its zone ID. It comes back as the same
  instant at `+01:00`, so `equals` against the original is false, and anything
  that depends on the zone's rules (the next daylight-saving change, say) is
  now wrong. The Workflow receives the same thing.
- **Build 3 - the Long.** Inside `Map<String, Object>` there is no declared
  type, so Jackson picks one: a number that fits in an int comes back an
  `Integer`. Cast it to `Long` and you get a `ClassCastException` at runtime.
- **Build 4 - the Instant.** Same cause: no declared type, so the ISO string
  stays a `String`. Nested objects come back as `LinkedHashMap`.

One more trap that follows from "the declared type does the work": declare a
field as an interface (a sealed `Payment` with `Card` and `Cash` records, say)
and Jackson cannot read it back. It throws "Cannot construct instance of ...
abstract types either need to be mapped to concrete types". Writing it works
fine, so the error surfaces wherever the value is read: in the Worker, not in
the Client that sent it.

The rule to give them: pass data, with concrete types. Records of records,
`List` and `Map` with real type parameters, `Instant` or `OffsetDateTime` for
time, the zone as its own `String` if you need it.
-->

---
layout: code-walkthrough
heading: A custom payload converter
notes:
  - The SDK's own <code>ObjectMapper</code> setup, plus one feature. Keep the zone ID
  - "<code>getEncodingType</code> names the format in each Payload's metadata"
  - "<code>toData</code>: value to bytes"
  - The encoding travels with the bytes
  - "<code>fromData</code>: bytes back to a value of the declared type"
  - Tried in order. The first converter that accepts the value wins
---

::code::

```java {all|2-4|6-8|10-16|14|22-25|32-36}
public class ZonedJsonConverter implements PayloadConverter {
  private final ObjectMapper mapper =
      JacksonJsonPayloadConverter.newDefaultObjectMapper()
          .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID);

  public String getEncodingType() {
    return "json/zoned";
  }

  public Optional<Payload> toData(Object value) {
    try {
      return Optional.of(
          Payload.newBuilder()
              .putMetadata("encoding", ByteString.copyFromUtf8(getEncodingType()))
              .setData(ByteString.copyFrom(mapper.writeValueAsBytes(value)))
              .build());
    } catch (JsonProcessingException e) {
      throw new DataConverterException(e);
    }
  }

  public <T> T fromData(Payload payload, Class<T> type, Type genericType) {
    try {
      return mapper.readValue(
          payload.getData().toByteArray(), mapper.constructType(genericType));
    } catch (IOException e) {
      throw new DataConverterException(e);
    }
  }
}

DataConverter converter =
    new DefaultDataConverter(
        new NullPayloadConverter(),
        new ByteArrayPayloadConverter(),
        new ZonedJsonConverter());
```

<!--
Checked against the Java SDK 1.40 source. Imports are cut for room.

Code: snippets/src/main/java/io/temporal/devdays/essentials/serialization/ZonedJsonConverter.java
and SerializationSetup.java

When the default is not enough, the payload converter is the extension point.
This one keeps zone IDs.

- **Build 1 - the mapper.** `newDefaultObjectMapper()` is the exact setup the
  SDK's own JSON converter uses (`java.time`, `Optional`, field access). One
  feature on top: write the zone ID after the offset.
- **Build 2 - `getEncodingType`.** Every Payload carries its encoding in its
  metadata, so the receiving side knows which converter to hand it to.
- **Build 3 - `toData`.** Value to bytes. Returning `Optional.empty()` means
  "not mine, try the next converter"; this one takes everything.
- **Build 4 - the metadata.** That `json/zoned` label is how the other side
  finds this converter again.
- **Build 5 - `fromData`.** Bytes back to a value, of the type the method
  signature declares: `genericType` keeps type parameters such as
  `List<Visit>`.
- **Build 6 - composition.** `DefaultDataConverter` tries converters in order
  on the way out, and picks by encoding on the way in. This list replaces the
  default JSON converter; the other two are the SDK's own.

The Java-specific part: the data converter runs on the Workflow thread,
because the Workflow itself serializes Activity arguments. So it must be
deterministic: no I/O, no clock, no randomness. Encryption with a KMS belongs in
a payload codec instead (next segment).

Alternatives worth naming: if all you need is a different `ObjectMapper`, pass
it to `new JacksonJsonPayloadConverter(mapper)` and swap it in with
`DefaultDataConverter.newDefaultInstance().withPayloadConverterOverrides(...)`,
which replaces the converter with the same encoding. For most teams, the
cheaper fix is the rule from the last slide: use concrete types.
-->

---
layout: code-walkthrough
heading: Register it on both sides
notes:
  - An object on the client options, not a path
  - The Client converts Workflow arguments and reads results
  - The Worker inherits the converter from the client it is built on
---

::code::

```java {all|1-2|4|5-6}
WorkflowClientOptions options =
    WorkflowClientOptions.newBuilder().setDataConverter(converter).build();

WorkflowClient client = WorkflowClient.newInstance(service, options);
WorkerFactory factory = WorkerFactory.newInstance(client);
Worker worker = factory.newWorker(taskQueue);
```

<!--
Code: snippets/src/main/java/io/temporal/devdays/essentials/serialization/SerializationSetup.java

- **Build 1 - the options.** `setDataConverter` on `WorkflowClientOptions`
  takes the converter object itself.
- **Build 2 - the Client.** Workflow arguments, Signal and Update arguments,
  Query and Workflow results.
- **Build 3 - the Worker.** A `WorkerFactory` takes its data converter from the
  `WorkflowClient` it is built on, so Activity arguments and results, and the
  Workflow's own inputs and outputs, use the same one. There is no separate
  Worker setting.

Register the same converter in every process: the starter, the Worker, and
anything that reads results. A Client without it gets a Payload whose encoding
it does not recognise and throws "No PayloadConverter is registered for an
encoding: json/zoned" when it reads the result. At runtime, not compile time.
-->
