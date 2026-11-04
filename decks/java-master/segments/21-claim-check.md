---
layout: section
---

<ZiggyHero scene="claim">

# The Claim-Check Pattern

Large payloads do not belong in the Event History.

</ZiggyHero>

<!--
Source: PPT slides 65-67, `{{SLIDE:claim-check-upload-activity}}` and
`{{SLIDE:claim-check-fetch-activity}}`. The Workflow and External Storage
slides are new to the TypeScript Deck.

Set it up before the code: every Activity argument and result is stored in the
Event History. Temporal rejects a single payload over 2 MB by default (fixed at
2 MB on Temporal Cloud), and terminates a Workflow whose Event History passes
50 MB. Put a 40 MB PDF through an Activity result and it never reaches the Workflow: the completion is
rejected and the Activity fails; put
a 1 MB one through twenty Activities and you are most of the way to the
history limit.

The fix is the oldest one in messaging: store the thing, pass the receipt.
-->

---
layout: code-walkthrough
heading: Store the bytes, return the key
notes:
  - The key is the claim check. A small string that stands in for a large object
  - Store the bytes anywhere. S3 here, but a bucket, a blob store or a database will do
  - Return the key. This is the only thing that enters the Event History
---

::code::

```ts {all|2|3-9|10}
export async function uploadToStorage(data: Buffer): Promise<string> {
  const key = randomUUID();
  await s3.send(
    new PutObjectCommand({
      Bucket: BUCKET,
      Key: key,
      Body: data,
    }),
  );
  return key;
}
```

<!--
Source: PPT slide 66. The original streamed the body with `Readable.from`; a
Buffer body is simpler and lets the SDK set the content length. The tests
behind this Deck run it against an in-memory stand-in for the S3 client.

Keep it simple: it is a thin wrapper around your blob storage client and it
should stay that way.

The trap in the original framing: this is *not* an Activity the Workflow calls
with the bytes. If the Workflow passed `data` as an Activity argument, the bytes
would already be in the Event History. Call it from inside the Activity that
*produces* the bytes (next-but-one slide), or from the Client before it starts
the Workflow.

`randomUUID()` from `node:crypto` is fine here because this runs outside the
Workflow sandbox. Inside a Workflow you would use `uuid4()` from
`@temporalio/workflow`. Worth pointing at: it is the determinism rule from the
design-patterns segment showing up in real code.

The guarantee to name: the Activity around this gets Temporal's full retry
treatment, so a transient S3 error does not lose the data. The cost of a retry
is a fresh key and, if the first upload succeeded before the Worker crashed,
an orphaned object. A bucket lifecycle rule cleans those up.
-->

---
layout: code-walkthrough
heading: Fetch by key
notes:
  - Takes the claim check back
  - Fetches the object by its key
  - Reads the whole body back into a Buffer
---

::code::

```ts {all|1|2-4|5-6}
export async function fetchFromStorage(key: string): Promise<Buffer> {
  const response = await s3.send(
    new GetObjectCommand({ Bucket: BUCKET, Key: key }),
  );
  const bytes = await response.Body!.transformToByteArray();
  return Buffer.from(bytes);
}
```

<!--
Source: PPT slide 67. The original reassembled the stream chunk by chunk;
`transformToByteArray()` is the AWS SDK v3 helper that does the same.

The mirror of the upload. Nothing clever.

If the object is too large to hold in memory, stream it instead: `Body` is a
readable stream, and the Activity can process it chunk by chunk, heartbeating
as it goes.
-->

---
layout: code-walkthrough
heading: The Workflow holds only the key
notes:
  - The Activity that produces the bytes stores them, and returns only the key
  - The Activity that needs the bytes fetches them by key
  - You own the object's lifecycle. Delete it on the way out
---

::code::

```ts {all|2|4|5-6}
export async function documentWorkflow(url: string): Promise<string> {
  const key = await downloadDocument(url);
  try {
    return await summarizeDocument(key);
  } finally {
    await deleteDocument(key);
  }
}
```

<!--
New to the TypeScript Deck.

- **Build 1 - produce.** `downloadDocument` is an Activity: it downloads the
  file and calls `uploadToStorage` itself, so the bytes never leave the
  Activity. The Workflow gets a 36-character string.
- **Build 2 - consume.** `summarizeDocument` calls `fetchFromStorage` with the
  key. Several downstream Activities can fetch with the same key: the bytes
  never pass through the Workflow, never enter the Event History, and are never
  duplicated. The Workflow holds a string and orchestrates gigabytes. The tests
  behind this Deck move a 3 MB document through this Workflow (over the 2 MB
  payload limit) and check that no payload in its Event History is over 1 KB.
- **Build 3 - clean up.** Temporal will not delete the object. Delete it in
  `finally` so it runs on the failure path too, or rely on a bucket lifecycle
  policy. If the Workflow is cancelled, run the delete inside
  `CancellationScope.nonCancellable`, the same move as a saga compensation.
-->

---
layout: code-walkthrough
heading: Or let the SDK do it
notes:
  - "External Storage: the claim check, built into the data converter"
  - A driver for your store. S3 and GCS drivers ship as packages
  - Payloads at or over this size are offloaded. 256 KiB is the default
  - Same converter on the Client and the Worker
---

::code::

```ts {all|2-5|3|4|8-9}
const dataConverter = {
  externalStorage: new ExternalStorage({
    drivers: [driver],
    payloadSizeThreshold: 256 * 1024,
  }),
};

const client = new Client({ connection, dataConverter });
const worker = await Worker.create({
  connection: nativeConnection,
  taskQueue,
  workflowsPath,
  activities,
  dataConverter,
});
```

<!--
New to the TypeScript Deck. External Storage is in Public Preview: APIs and
configuration may change before General Availability. Checked against
@temporalio/common 1.24 and docs.temporal.io/external-storage.

The hand-rolled version is worth understanding. Since SDK 1.21 the SDK can do it
for you, transparently, for every payload.

- **Build 1 - `ExternalStorage`.** Set on the data converter. Any payload over
  the threshold is uploaded by a driver and replaced in the Event History by a
  small reference; the receiving side downloads it again before your code sees
  it. Workflow and Activity code do not change.
- **Build 2 - the driver.** `@temporalio/external-storage-s3` and
  `@temporalio/external-storage-gcs` ship with the SDK, or implement the
  `StorageDriver` interface yourself: a `name`, a `type`, `store` and
  `retrieve`.
- **Build 3 - the threshold.** 256 KiB by default. Set it to 0 to externalize
  everything, for example when data must stay in infrastructure you control.
- **Build 4 - both sides.** A Client without it cannot read an offloaded
  result.

This segment now comes before Serialization and Encryption, so the room has not
met the data converter yet. One sentence covers it: the data converter is the
SDK's hook for turning every argument and result into bytes on the way out and
back on the way in; the Serialization segment comes back to it.

It runs after the payload codec, so with an encryption codec the store only
ever holds ciphertext. And like a codec, it runs outside the Workflow sandbox,
so it takes the driver object directly rather than a module path.

The same caveat as the hand-rolled version: you own the bucket's lifecycle.
Keep objects for at least as long as the Workflow and its retention period.
-->
