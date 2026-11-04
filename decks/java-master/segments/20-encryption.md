---
layout: section
---

<ZiggyHero scene="lock">

# Encryption

Payloads leave your Worker. Encrypt them before they do.

</ZiggyHero>

<!--
Source: PPT slides 63-64, `{{SLIDE:encryption-codec-in-practice}}`. The
registration and Codec Server slides are new to the TypeScript Deck.

PORTED: the original showed C#'s `IPayloadCodec`. The TypeScript interface is
`PayloadCodec` from `@temporalio/common`, with `encode`/`decode` rather than
`EncodeAsync`/`DecodeAsync`.
-->

---
layout: code-walkthrough
heading: A codec in practice
notes:
  - "<code>PayloadCodec</code>: two async methods over arrays of Payloads"
  - "<code>encode</code> runs after the payload converter, before anything leaves the process"
  - The whole Payload, metadata included, is serialized and encrypted
  - A new encoding label, so the other side knows to decrypt
  - "<code>decode</code> runs when data arrives back"
  - Not encrypted? Pass it through untouched
---

::code::

```ts {all|3|6|10|9|15|18-20}
const ENCODING = "binary/encrypted";

export class EncryptionCodec implements PayloadCodec {
  constructor(private readonly key: CryptoKey) {}

  async encode(payloads: Payload[]): Promise<Payload[]> {
    return Promise.all(
      payloads.map(async (payload) => ({
        metadata: { [METADATA_ENCODING_KEY]: utf8(ENCODING) },
        data: await encrypt(this.key, PayloadProto.encode(payload).finish()),
      })),
    );
  }

  async decode(payloads: Payload[]): Promise<Payload[]> {
    return Promise.all(
      payloads.map(async (payload) =>
        isEncrypted(payload)
          ? PayloadProto.decode(await decrypt(this.key, payload.data!))
          : payload,
      ),
    );
  }
}
```

<!--
Source: PPT slide 64, ported from C#. The imports and the small helpers are
cut for room: `encrypt`/`decrypt` are AES-GCM with Node's Web Crypto, `utf8`
is a `TextEncoder`, and `PayloadProto` is `temporal.api.common.v1.Payload`
from `@temporalio/proto`. This is the shape of Temporal's own encryption sample.

Walk the interface. Two methods, both over arrays, both async.

- **Build 1 - the interface.** `PayloadCodec` is the whole contract.
- **Build 2 - `encode`.** Called on the way out, after the payload converter
  from the last segment has turned your values into Payloads, and before
  anything is sent to the Temporal Service. This is the last point at which
  the bytes are yours.
- **Build 3 - what gets encrypted.** The entire Payload, including its original
  encoding label, so nothing about the value leaks.
- **Build 4 - the label.** The outer Payload says only `binary/encrypted`. A
  real codec also records a key ID here, so keys can rotate.
- **Build 5 - `decode`.** Called on the way in. Symmetric by construction.
- **Build 6 - pass-through.** Anything not encrypted, such as data written
  before you turned encryption on, is left alone.

The contrast with the payload converter, and the reason both exist: a payload
converter runs inside the Workflow sandbox and must be synchronous. A codec runs
outside it, on the Client and on the Worker's main thread, so it can be async
and can call a KMS. That is where encryption and compression belong.
-->

---
layout: code-walkthrough
heading: Same codec, Client and Worker
notes:
  - An object this time, not a path. Codecs never enter the Workflow sandbox
  - The Client encodes Workflow arguments and decodes results
  - The Worker does the rest. Workflow code does not change at all
---

::code::

```ts {all|1|3|4-9}
const dataConverter = { payloadCodecs: [new EncryptionCodec(key)] };

const client = new Client({ connection, dataConverter });
const worker = await Worker.create({
  connection: nativeConnection,
  taskQueue,
  workflowsPath,
  dataConverter,
});
```

<!--
Source: PPT slide 64, the registration lines of the original, split out.

- **Build 1 - the codec list.** `payloadCodecs` is an array: encoding runs in
  array order and decoding in reverse, so `[compression, encryption]`
  compresses first, then encrypts.
- **Build 2 - the Client.** Miss it here and the Client cannot read the result:
  it gets a `binary/encrypted` Payload and throws "Unknown encoding". At
  runtime, not compile time.
- **Build 3 - the Worker.** Same codec, same key.

The claim worth making explicitly, because it is the reason the pattern exists:
Workflow code does not change at all. Not one line. Encryption is a transport
concern and it stays one. The tests behind this Deck run a Workflow whose code
has never heard of encryption, and check that the Event History holds only
ciphertext.
-->

---
layout: default
---

# Who can read what

- **Temporal Service:** ciphertext only. It never has the key
- **Your Clients and Workers:** plaintext, through the codec
- **Web UI and CLI:** ciphertext, until you run a **Codec Server**

<!--
Source: PPT slide 64's speaker notes, given their own slide here.

The consequence somebody always spots: the Temporal Service now stores bytes it
cannot read, so the Web UI and the CLI show ciphertext.

The fix is a Codec Server: a small HTTP service you run that exposes the same
codec's `decode` (and `encode`). The Web UI calls it from the user's browser to
decode for display, and the CLI takes `--codec-endpoint`. The keys stay on your
side; the Temporal Service never sees them. Put authentication in front of it,
because anyone who can call it can decrypt.

Mention it; do not demo it unless asked.
-->
