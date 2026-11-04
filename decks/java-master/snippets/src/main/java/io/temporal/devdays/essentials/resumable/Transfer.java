package io.temporal.devdays.essentials.resumable;

/** A bank transfer. {@code toAccount} is the field an operator may have to correct. */
public record Transfer(String fromAccount, String toAccount, int amount) {
  public Transfer withToAccount(String account) {
    return new Transfer(fromAccount, account, amount);
  }
}
