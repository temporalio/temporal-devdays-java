---
layout: section
---

<ZiggyHero scene="claim">

# The Claim-Check Pattern

Large payloads do not belong in the Event History.

</ZiggyHero>

<!--
Source: PPT slides 65-67, `{{SLIDE:claim-check-upload-activity}}` and
`{{SLIDE:claim-check-fetch-activity}}`.

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
  - Store the bytes anywhere. A bucket, a blob store or a database will do
  - Return the key. This is the only thing that enters the Event History
---

::code::

```java {all|2|3|4}
public String uploadToStorage(byte[] data) {
  String key = UUID.randomUUID().toString();
  blobs.put(BUCKET, key, data);
  return key;
}
```

<!--
Source: PPT slide 66. `blobs` is a small `BlobStore` interface (`put`, `get`,
`delete`) in front of your store's client: an S3 `PutObject`, a GCS upload, a
row in a table. The tests behind this Deck run it against an in-memory one.

Code: snippets/src/main/java/io/temporal/devdays/essentials/claimcheck/ClaimCheckStorage.java

Keep it simple: it is a thin wrapper around your blob storage client and it
should stay that way.

The trap in the original framing: this is *not* an Activity the Workflow calls
with the bytes. If the Workflow passed `data` as an Activity argument, the bytes
would already be in the Event History. Call it from inside the Activity that
*produces* the bytes (next-but-one slide), or from the Client before it starts
the Workflow.

`UUID.randomUUID()` is fine here because this runs in an Activity, not in
Workflow code. Inside a Workflow you would use `Workflow.randomUUID()`. Worth
pointing at: it is the determinism rule from the design-patterns segment
showing up in real code.

The guarantee to name: the Activity around this gets Temporal's full retry
treatment, so a transient storage error does not lose the data. The cost of a
retry is a fresh key and, if the first upload succeeded before the Worker
crashed, an orphaned object. A bucket lifecycle rule cleans those up.
-->

---
layout: code-walkthrough
heading: Fetch by key
notes:
  - Takes the claim check back
  - Fetches the object by its key
  - A missing key will not appear on retry. Fail it for good
---

::code::

```java {all|1|2|3-5}
public byte[] fetchFromStorage(String key) {
  byte[] bytes = blobs.get(BUCKET, key);
  if (bytes == null) {
    throw ApplicationFailure.newNonRetryableFailure("No object " + key, "NoSuchKey");
  }
  return bytes;
}
```

<!--
Source: PPT slide 67, which reassembled a stream chunk by chunk; this reads
the whole object, and adds the missing-key case.

Code: snippets/src/main/java/io/temporal/devdays/essentials/claimcheck/ClaimCheckStorage.java

The mirror of the upload. Nothing clever.

- **Build 3 - the missing key.** A network error is worth retrying; a key that
  is not there will still not be there next time. A non-retryable
  `ApplicationFailure` fails the Activity at once, with a type the Workflow can
  match on. The tests behind this Deck check it.

If the object is too large to hold in memory, stream it instead: read the
store's `InputStream` and process it chunk by chunk, heartbeating as you go.
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

```java {all|3|5|7}
@Override
public String process(String url) {
  String key = activities.downloadDocument(url);
  try {
    return activities.summarizeDocument(key);
  } finally {
    activities.deleteDocument(key);
  }
}
```

<!--
Code: snippets/src/main/java/io/temporal/devdays/essentials/claimcheck/DocumentWorkflowImpl.java

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
  `Workflow.newDetachedCancellationScope(...)`, the same move as a saga
  compensation.
-->

---
layout: code-walkthrough
heading: Or let the SDK do it
notes:
  - "External Storage: the claim check, built into the client options"
  - A driver for your store. An S3 driver ships as a package
  - Payloads at or over this size are offloaded. 256 KiB is the default
  - The Worker inherits it from the client it is built on
---

::code::

```java {all|1-5|3|4|7-10}
ExternalStorage storage =
    ExternalStorage.newBuilder()
        .setDriver(driver)
        .setPayloadSizeThreshold(256 * 1024)
        .build();

WorkflowClient client =
    WorkflowClient.newInstance(
        service, WorkflowClientOptions.newBuilder().setExternalStorage(storage).build());
WorkerFactory factory = WorkerFactory.newInstance(client);
Worker worker = factory.newWorker(taskQueue);
```

<!--
External Storage is marked `@Experimental` in the Java SDK 1.40: APIs and
configuration may change. Checked against the 1.40 source; the tests behind
this Deck offload a 1 MB Activity result through an in-memory driver and check
that the Workflow still sees all 1 MB while the Event History holds only a
small reference. (The 1.40 Javadoc on `setExternalStorage` still calls it a
no-op; the test shows it is not.)

Code: snippets/src/main/java/io/temporal/devdays/essentials/claimcheck/ExternalStorageSetup.java
and InMemoryDriver.java

The hand-rolled version is worth understanding. The SDK can now do it for you,
transparently, for every payload.

- **Build 1 - `ExternalStorage`.** Set on `WorkflowClientOptions`. Any payload
  over the threshold is uploaded by a driver and replaced in the Event History
  by a small reference; the receiving side downloads it again before your code
  sees it. Workflow and Activity code do not change.
- **Build 2 - the driver.** `io.temporal:temporal-payload-storage-s3driver`
  ships an S3 driver, or implement the `StorageDriver` interface yourself: a
  `getName`, a `getType`, `store` and `retrieve`. Register several and add a
  `StorageDriverSelector` to choose between them.
- **Build 3 - the threshold.** 256 KiB by default. Set it to 0 to externalize
  everything, for example when data must stay in infrastructure you control.
- **Build 4 - both sides.** Like the data converter, a `WorkerFactory` takes
  External Storage from the client it is built on. A Client without it cannot
  read an offloaded result.

This segment now comes before Serialization and Encryption, so the room has not
met the data converter yet. One sentence covers it: the data converter is the
SDK's hook for turning every argument and result into bytes on the way out and
back on the way in; the Serialization segment comes back to it.

It runs after the payload codec, so with an encryption codec the store only
ever holds ciphertext.

The same caveat as the hand-rolled version: you own the bucket's lifecycle.
Keep objects for at least as long as the Workflow and its retention period.
-->
