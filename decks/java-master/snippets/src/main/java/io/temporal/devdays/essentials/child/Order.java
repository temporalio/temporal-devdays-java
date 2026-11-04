package io.temporal.devdays.essentials.child;

import java.util.List;

/** One customer order: an ID and the items to ship. Same shape as the Child Workflows Workshop. */
public record Order(String orderId, List<String> items) {}
