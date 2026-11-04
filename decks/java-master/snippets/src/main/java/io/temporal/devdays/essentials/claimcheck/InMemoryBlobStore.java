package io.temporal.devdays.essentials.claimcheck;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** An in-memory stand-in for a bucket, so the tests can see what is stored and what is deleted. */
public class InMemoryBlobStore implements BlobStore {
  private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

  @Override
  public void put(String bucket, String key, byte[] data) {
    objects.put(bucket + "/" + key, data);
  }

  @Override
  public byte[] get(String bucket, String key) {
    return objects.get(bucket + "/" + key);
  }

  @Override
  public void delete(String bucket, String key) {
    objects.remove(bucket + "/" + key);
  }

  public int size() {
    return objects.size();
  }
}
