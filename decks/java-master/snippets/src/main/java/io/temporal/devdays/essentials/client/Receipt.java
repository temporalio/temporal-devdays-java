package io.temporal.devdays.essentials.client;

/** The result of the Workflow the Client in 07b-client.md starts. */
public record Receipt(String transferId, String status) {}
