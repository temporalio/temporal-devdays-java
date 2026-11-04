package io.temporal.devdays.essentials.workflows;

import io.temporal.failure.ApplicationFailure;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Not on a slide: an in-memory ledger stands in for the bank, so a test can check balances and
 * close an account to make a deposit fail.
 */
public class BankActivitiesImpl implements BankActivities {
  public final Map<String, Long> balances = new ConcurrentHashMap<>();
  public final Set<String> closed = ConcurrentHashMap.newKeySet();

  private void move(String account, long amount) {
    balances.merge(account, amount, Long::sum);
  }

  @Override
  public boolean checkFraud(Transfer transfer) {
    return transfer.amount() > 10_000;
  }

  @Override
  public void withdraw(Transfer transfer) {
    move(transfer.fromAccount(), -transfer.amount());
  }

  @Override
  public void deposit(Transfer transfer) {
    if (closed.contains(transfer.toAccount())) {
      throw ApplicationFailure.newNonRetryableFailure(
          "account " + transfer.toAccount() + " is closed", "AccountClosed");
    }
    move(transfer.toAccount(), transfer.amount());
  }

  @Override
  public void refund(Transfer transfer) {
    move(transfer.fromAccount(), transfer.amount());
  }
}
