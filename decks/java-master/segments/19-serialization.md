---
layout: section
---

<ZiggyHero scene="pack">

# Data Serialization

Everything that crosses the boundary gets converted.

</ZiggyHero>

<!--
Source: PPT slides 59-60, `{{BLOCK:module-serialization-demo}}`. The JSON
traps, custom payload converter and registration slides are new to the
TypeScript Deck.

PORTED: the original was a C# slide leaning on `[DataContract]` / `[DataMember]`.
TypeScript has no such attributes, which is itself the point of this segment.
-->

---
layout: code-walkthrough
heading: Data serialization
notes:
  - Plain interfaces. No attribute, no decorator, no base class
  - Workflow arguments are serialized by the Client and deserialized by the Worker
  - Activity arguments and results make the same trip
  - So does the Workflow result. Every value here makes a round trip through JSON
---

::code::

```ts {all|1-5|7|9|11-12}
export interface Claim {
  patientId: string;
  drugNdc: string;
  billedAmountCents: number;
}

export async function claimWorkflow(claim: Claim): Promise<Result> {
  // `claim` was JSON on the wire a moment ago
  const result = await processClaim(claim);

  // and this crosses the wire on the way back out
  return result;
}
```

<!--
Source: PPT slide 60.

The default data converter is JSON. In TypeScript that means the ergonomics are
excellent and the traps are quiet, so name the traps (next slide).

- **Build 1 - the shape.** Unlike .NET or Java, there is nothing to annotate.
  An interface is erased at compile time; nothing checks at runtime that the
  data matches it.
- **Build 2 - arguments in.** Serialized by the Client, deserialized by the
  Worker, and they may be different processes on different machines.
- **Build 3 - Activity calls.** `processClaim` is an Activity: its argument is
  serialized by the Workflow, stored in the Event History, and handed to
  whichever Worker picks up the Activity Task. Its result comes back the same
  way.
- **Build 4 - results out.** Same trip in reverse, to whoever is waiting on the
  Workflow.

The default converter handles `undefined`, `Uint8Array` (as raw bytes), and
anything `JSON.stringify` can handle, in that order.

Payload size matters: every argument and result is stored in the Event
History, and Temporal rejects a single payload over 2 MB by default. That is
the problem the Claim-Check segment already solved.
-->

---
layout: code-walkthrough
heading: What JSON does to your values
notes:
  - A Date, a Set and an undefined property go in
  - The Date comes back a string. TypeScript still says <code>Date</code>
  - The Set comes back an empty object
  - The undefined property is gone
---

::code::

```ts {all|4-6|13|14|15}
const received = await client.workflow.execute(echo, {
  args: [
    {
      at: new Date("2026-01-01T09:00:00Z"),
      tags: new Set(["urgent"]),
      copay: undefined,
    },
  ],
  taskQueue,
  workflowId,
});

received.at; // "2026-01-01T09:00:00.000Z": a string, typed as Date
received.tags; // {}: the Set is gone
"copay" in received; // false: undefined properties are dropped
```

<!--
New to the TypeScript Deck. `echo` is a Workflow that returns its argument,
and the tests behind this Deck assert each of these three results.

- **Build 1 - the input.** Three ordinary values, all legal TypeScript for the
  `Visit` type the Workflow declares.
- **Build 2 - the Date.** It goes in as a `Date` and comes out as an ISO
  string, and the compiler will not warn you, because the *type* still says
  `Date`. Call `received.at.getTime()` and it throws at runtime. The same is
  true inside the Workflow: the argument it receives has a string there too.
- **Build 3 - the Set.** `Map` and `Set` serialize as `{}`. The contents are
  silently lost.
- **Build 4 - undefined.** The property is dropped entirely, not set to
  `undefined`.

Two more traps that follow from "it is JSON":

- A class instance arrives as a plain object. Methods are gone and
  `instanceof` is false.
- A `BigInt` fails before anything is sent: `JSON.stringify` cannot handle it,
  so `start` throws "Unable to convert ... to payload".

The rule to give them: pass data, not objects. If the Workflow needs behaviour,
it imports the function; it does not receive it. Dates go over as ISO strings
or epoch numbers, typed as such.
-->

---
layout: code-walkthrough
heading: A custom payload converter
notes:
  - "<code>encodingType</code> names the format in each Payload's metadata"
  - "<code>toPayload</code>: value to bytes. <code>tagDates</code> wraps each Date before JSON flattens it"
  - The encoding travels with the bytes
  - "<code>fromPayload</code>: bytes back to a value, Dates revived"
  - Tried in order. The first converter that accepts the value wins
---

::code::

```ts {all|2|4-9|7|12-14|17-21}
class DateJsonConverter implements PayloadConverterWithEncoding {
  readonly encodingType = "json/dated";

  toPayload(value: unknown): Payload | undefined {
    if (value === undefined) return undefined;
    return {
      metadata: { [METADATA_ENCODING_KEY]: encode(this.encodingType) },
      data: encode(JSON.stringify(value, tagDates)),
    };
  }

  fromPayload<T>(payload: Payload): T {
    return JSON.parse(decode(payload.data!), reviveDates);
  }
}

export const payloadConverter = new CompositePayloadConverter(
  new UndefinedPayloadConverter(),
  new BinaryPayloadConverter(),
  new DateJsonConverter(),
);
```

<!--
New to the TypeScript Deck. Checked against @temporalio/common 1.24; the
imports and the three small helpers (`encode`, `tagDates`, `reviveDates`) are
cut for room.

When JSON is not enough, the payload converter is the extension point. This one
keeps Dates as Dates.

- **Build 1 - `encodingType`.** Every Payload carries its encoding in its
  metadata, so the receiving side knows which converter to hand it to.
- **Build 2 - `toPayload`.** Value to bytes. `tagDates` is a `JSON.stringify`
  replacer that turns each `Date` into `{ $date: "..." }` before JSON flattens
  it to a plain string. Returning `undefined` means "not mine, try the next
  converter".
- **Build 3 - the metadata.** That `json/dated` label is how the other side
  finds this converter again.
- **Build 4 - `fromPayload`.** Bytes back to a value; the reviver turns each
  `$date` back into a `Date`.
- **Build 5 - composition.** `CompositePayloadConverter` tries converters in
  order on the way out, and picks by encoding on the way in. This one replaces
  the default JSON converter; the other two are the SDK's own.

The TypeScript-specific part: this module runs *inside the Workflow sandbox*,
because the Workflow itself serializes Activity arguments. So it must be
deterministic and sandbox-safe: no Node built-ins, no I/O, nothing async. That
is why the methods are synchronous. Anything that needs I/O, like encryption
with a KMS, belongs in a payload codec instead (next segment).

Alternatives worth naming: the SDK ships `DefaultPayloadConverterWithProtobufs`
for protobuf messages. For most teams, the cheaper fix is the rule from the
last slide: pass ISO strings.
-->

---
layout: code-walkthrough
heading: Register it on both sides
notes:
  - A <strong>path</strong>, not an object. The Worker bundles this module into the Workflow sandbox
  - The Client converts Workflow arguments and reads results
  - The Worker converts everything else, in and out of the sandbox
---

::code::

```ts {all|2|5|6-11}
const dataConverter = {
  payloadConverterPath: require.resolve("./payload-converter"),
};

const client = new Client({ connection, dataConverter });
const worker = await Worker.create({
  connection: nativeConnection,
  taskQueue,
  workflowsPath,
  dataConverter,
});
```

<!--
New to the TypeScript Deck.

- **Build 1 - the path.** Other SDKs take a converter object. TypeScript takes a
  module path, because the Workflow sandbox is a separate V8 context: the
  Worker bundles the module named here into the Workflow code, and loads it
  again on the main thread for Activities. The module must have a named export
  called `payloadConverter`.
- **Build 2 - the Client.** Workflow arguments, Signal and Update arguments,
  Query and Workflow results.
- **Build 3 - the Worker.** Activity arguments and results, and the Workflow's
  own inputs and outputs.

Register the same converter everywhere. A Client without it gets a Payload
whose encoding it does not recognise and throws "Unknown encoding" when it
reads the result. If you bundle Workflow code ahead of time with
`bundleWorkflowCode`, pass `payloadConverterPath` there too.
-->
