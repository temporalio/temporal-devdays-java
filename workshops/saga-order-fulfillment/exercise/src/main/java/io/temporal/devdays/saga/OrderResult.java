package io.temporal.devdays.saga;

/** What a fulfilled order returns. */
public record OrderResult(String orderId, String trackingNumber) {}
