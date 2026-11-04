package io.temporal.devdays.essentials.claimcheck;

/**
 * The thin interface the claim-check slides put in front of a blob store: S3, GCS, Azure Blob, or a
 * database table. The tests use {@link InMemoryBlobStore}.
 */
public interface BlobStore {
  void put(String bucket, String key, byte[] data);

  /** The object's bytes, or {@code null} if there is no such key. */
  byte[] get(String bucket, String key);

  void delete(String bucket, String key);
}
