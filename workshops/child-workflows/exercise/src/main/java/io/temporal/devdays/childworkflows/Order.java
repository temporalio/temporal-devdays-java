package io.temporal.devdays.childworkflows;

import java.util.List;

/** One customer order: an ID and the items to ship or pack. */
public record Order(String orderId, List<String> items) {}
