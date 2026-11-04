package io.temporal.devdays.essentials.updates;

/** The Client line on the comparison slide in 11-updates.md. */
public final class UpdatesClient {
  private UpdatesClient() {}

  public static int withdrawFifty(AccountWorkflow account) {
    // slide: 11-updates.md "Signals, Queries, and Updates"
    int balance = account.withdraw(50);
    // end slide
    return balance;
  }
}
