---
layout: section
---

<ZiggyHero scene="lock">

# Encryption

Payloads leave your Worker. Encrypt them before they do.

</ZiggyHero>

<!--
Source: PPT slides 63-64, `{{SLIDE:encryption-codec-in-practice}}`.

The Java interface is `PayloadCodec` from `io.temporal.payload.codec`, with
`encode` and `decode` over lists of Payloads.
-->

---
layout: code-walkthrough
heading: A codec in practice
notes:
  - "<code>PayloadCodec</code>: two methods over lists of Payloads"
  - "<code>encode</code> runs after the payload converter, before anything leaves the process"
  - The whole Payload, metadata included, is serialized and encrypted
  - A new encoding label, so the other side knows to decrypt
  - "<code>decode</code> runs when data arrives back"
  - Not encrypted? Pass it through untouched
---

::code::

```java {all|1|5|11|10|17|19}
public record EncryptionCodec(SecretKey key) implements PayloadCodec {
  private static final String ENCODING = "binary/encrypted";

  @Override
  public List<Payload> encode(List<Payload> payloads) {
    return payloads.stream()
        .map(
            payload ->
                Payload.newBuilder()
                    .putMetadata("encoding", ByteString.copyFromUtf8(ENCODING))
                    .setData(ByteString.copyFrom(encrypt(key, payload.toByteArray())))
                    .build())
        .toList();
  }

  @Override
  public List<Payload> decode(List<Payload> payloads) {
    return payloads.stream()
        .map(payload -> isEncrypted(payload) ? decrypt(key, payload.getData()) : payload)
        .toList();
  }
}
```

<!--
Source: PPT slide 64. The imports and the small helpers are cut for room:
`encrypt`/`decrypt` are AES-GCM with `javax.crypto` and a random IV per
Payload, `decrypt` also parses the inner Payload back with `Payload.parseFrom`,
and `isEncrypted` checks the encoding label. This is the shape of the
encryption sample in samples-java.

Code: snippets/src/main/java/io/temporal/devdays/essentials/encryption/EncryptionCodec.java
and AesGcm.java

Walk the interface. Two methods, both over lists.

- **Build 1 - the interface.** `PayloadCodec` is the whole contract. A record
  is enough to hold the key: the codec has no other state.
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
converter turns values into bytes; a codec turns bytes into other bytes, so it
works on any value without knowing its type. That is where encryption and
compression belong.

A Java detail worth knowing: both are synchronous, and both run wherever the
data converter runs, which includes the Workflow thread (the Workflow
serializes its own Activity arguments). Do not call a KMS on every `encode`:
the Worker's deadlock detector flags a Workflow thread that does not yield for
a second. Fetch and cache the data key, and encrypt locally.
-->

---
layout: code-walkthrough
heading: Same codec, Client and Worker
notes:
  - "<code>CodecDataConverter</code> wraps the default converter with your codecs"
  - The Client encodes Workflow arguments and decodes results
  - The Worker inherits the converter from its client. Workflow code does not change at all
---

::code::

```java {all|1-4|6-8|9-10}
DataConverter converter =
    new CodecDataConverter(
        DefaultDataConverter.newDefaultInstance(),
        List.of(new EncryptionCodec(key)));

WorkflowClient client =
    WorkflowClient.newInstance(
        service, WorkflowClientOptions.newBuilder().setDataConverter(converter).build());
WorkerFactory factory = WorkerFactory.newInstance(client);
Worker worker = factory.newWorker(taskQueue);
```

<!--
Source: PPT slide 64, the registration lines of the original, split out.

Code: snippets/src/main/java/io/temporal/devdays/essentials/encryption/EncryptionSetup.java

- **Build 1 - the codec list.** `CodecDataConverter` runs the payload converter
  first, then the codecs. The list order matters: on the way out the codecs
  are applied last to first, so the earlier ones wrap the later ones.
  `List.of(encryption, compression)` compresses first, then encrypts.
- **Build 2 - the Client.** Miss it here and the Client cannot read the result:
  it gets a `binary/encrypted` Payload and throws "No PayloadConverter is
  registered for an encoding: binary/encrypted". At runtime, not compile time.
- **Build 3 - the Worker.** A `WorkerFactory` uses the data converter of the
  client it is built on: same codec, same key. In a separate Worker process,
  build its client the same way.

The claim worth making explicitly, because it is the reason the pattern exists:
Workflow code does not change at all. Not one line. Encryption is a transport
concern and it stays one. The tests behind this Deck run a Workflow whose code
has never heard of encryption, and check that the Event History holds only
ciphertext.

Failures are a separate switch: Payloads inside a failure's details are always
encoded, but the message and stack trace are not unless you pass `true` as the
third argument to `CodecDataConverter`.
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
