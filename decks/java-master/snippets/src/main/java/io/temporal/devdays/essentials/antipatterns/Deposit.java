package io.temporal.devdays.essentials.antipatterns;

/** A deposited cheque, held for three days before its funds are released. */
public record Deposit(String depositId) {}
