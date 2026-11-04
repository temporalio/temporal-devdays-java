package io.temporal.devdays.essentials.antipatterns;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in core-banking ledger for the idempotency slide. It ignores a posting whose idempotency
 * key it has already seen, and can be told to fail once after committing: the
 * timeout-after-a-successful-write that makes a retry dangerous.
 */
public class Ledger {
  /** Postings by idempotency key: what actually hit the account. */
  public final Map<String, Debit> postings = new ConcurrentHashMap<>();

  /** Every request's idempotency key, in order, duplicates included. */
  public final List<String> requests = new CopyOnWriteArrayList<>();

  private final AtomicInteger failuresAfterCommit = new AtomicInteger();

  public void failOnceAfterCommit() {
    failuresAfterCommit.set(1);
  }

  public String post(Debit debit, String idempotencyKey) {
    requests.add(idempotencyKey);
    postings.putIfAbsent(idempotencyKey, debit);
    if (failuresAfterCommit.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
      throw new IllegalStateException("connection reset after commit");
    }
    return idempotencyKey;
  }
}
