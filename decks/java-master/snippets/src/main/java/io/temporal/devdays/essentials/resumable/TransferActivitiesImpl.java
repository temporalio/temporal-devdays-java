package io.temporal.devdays.essentials.resumable;

import io.temporal.failure.ApplicationFailure;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Activity the Resumable Activity slide retries. An account the bank does not know is a
 * permanent failure: retrying the same input can never succeed, so it fails at once instead of
 * burning the Retry Policy's attempts.
 */
public class TransferActivitiesImpl implements TransferActivities {
  public static final Set<String> KNOWN_ACCOUNTS = Set.of("10000001", "12345678");

  /** The destination account of every attempt, in order. */
  public final List<String> attempts = new CopyOnWriteArrayList<>();

  @Override
  public String executeTransfer(Transfer transfer) {
    attempts.add(transfer.toAccount());
    if (!KNOWN_ACCOUNTS.contains(transfer.toAccount())) {
      throw ApplicationFailure.newNonRetryableFailure(
          "Account " + transfer.toAccount() + " not found", "AccountNotFoundError");
    }
    return "Transfer of " + transfer.amount() + " to " + transfer.toAccount() + " completed";
  }
}
