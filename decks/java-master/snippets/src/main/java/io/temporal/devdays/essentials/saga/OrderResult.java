package io.temporal.devdays.essentials.saga;

/** What a fulfilled order returns. */
public record OrderResult(String orderId, String trackingNumber) {}
