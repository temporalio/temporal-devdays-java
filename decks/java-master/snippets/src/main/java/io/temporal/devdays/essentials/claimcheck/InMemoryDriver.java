package io.temporal.devdays.essentials.claimcheck;

import io.temporal.api.common.v1.Payload;
import io.temporal.payload.storage.StorageDriver;
import io.temporal.payload.storage.StorageDriverClaim;
import io.temporal.payload.storage.StorageDriverRetrieveContext;
import io.temporal.payload.storage.StorageDriverStoreContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** A StorageDriver that keeps offloaded Payloads in a map, so the tests can count them. */
public class InMemoryDriver implements StorageDriver {
  public final Map<String, Payload> stored = new ConcurrentHashMap<>();

  @Override
  public String getName() {
    return "memory";
  }

  @Override
  public String getType() {
    return "devdays.memory";
  }

  @Override
  public CompletableFuture<List<StorageDriverClaim>> store(
      StorageDriverStoreContext context, List<Payload> payloads) {
    List<StorageDriverClaim> claims =
        payloads.stream()
            .map(
                payload -> {
                  String key = UUID.randomUUID().toString();
                  stored.put(key, payload);
                  return new StorageDriverClaim(Map.of("key", key));
                })
            .toList();
    return CompletableFuture.completedFuture(claims);
  }

  @Override
  public CompletableFuture<List<Payload>> retrieve(
      StorageDriverRetrieveContext context, List<StorageDriverClaim> claims) {
    return CompletableFuture.completedFuture(
        claims.stream().map(claim -> stored.get(claim.getClaimData().get("key"))).toList());
  }
}
