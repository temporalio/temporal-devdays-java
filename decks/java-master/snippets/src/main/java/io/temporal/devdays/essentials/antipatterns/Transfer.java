package io.temporal.devdays.essentials.antipatterns;

/** A money transfer, identified by the ID the customer's app gave it. */
public record Transfer(String transferId) {}
