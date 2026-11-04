package io.temporal.devdays.essentials.antipatterns;

/** A debit to post to an account. */
public record Debit(String accountId, int amount) {}
