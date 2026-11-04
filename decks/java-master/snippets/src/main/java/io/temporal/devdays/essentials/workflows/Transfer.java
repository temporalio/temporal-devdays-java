package io.temporal.devdays.essentials.workflows;

/** One transfer between two accounts. Not on a slide. */
public record Transfer(String transferId, String fromAccount, String toAccount, long amount) {}
