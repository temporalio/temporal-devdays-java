package io.temporal.devdays.essentials.claimcheck;

import io.temporal.failure.ApplicationFailure;
import java.util.UUID;

/**
 * Store the bytes, hand back a key. Called from inside the Activities that produce and consume the
 * bytes, never by the Workflow with the bytes in hand.
 */
public class ClaimCheckStorage {
  static final String BUCKET = "documents";
  private final BlobStore blobs;

  public ClaimCheckStorage(BlobStore blobs) {
    this.blobs = blobs;
  }

  // slide: 21-claim-check.md "Store the bytes, return the key"
  public String uploadToStorage(byte[] data) {
    String key = UUID.randomUUID().toString();
    blobs.put(BUCKET, key, data);
    return key;
  }
  // end slide

  // slide: 21-claim-check.md "Fetch by key"
  public byte[] fetchFromStorage(String key) {
    byte[] bytes = blobs.get(BUCKET, key);
    if (bytes == null) {
      throw ApplicationFailure.newNonRetryableFailure("No object " + key, "NoSuchKey");
    }
    return bytes;
  }
  // end slide

  public void deleteFromStorage(String key) {
    blobs.delete(BUCKET, key);
  }
}
