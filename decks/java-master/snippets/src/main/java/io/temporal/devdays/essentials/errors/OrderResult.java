package io.temporal.devdays.essentials.errors;

/** What a Workflow returns for an order it processed. */
public record OrderResult(String orderId) {}
