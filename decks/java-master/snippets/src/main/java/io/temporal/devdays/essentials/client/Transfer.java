package io.temporal.devdays.essentials.client;

/** The input of the Workflow the Client in 07b-client.md starts. */
public record Transfer(String transferId, String fromAccount, String toAccount, int amount) {}
