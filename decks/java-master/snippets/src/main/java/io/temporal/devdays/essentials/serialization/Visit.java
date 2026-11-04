package io.temporal.devdays.essentials.serialization;

import java.time.ZonedDateTime;
import java.util.Map;

/** A visit: when it happened, in the clinic's zone, and a bag of loosely typed extras. */
public record Visit(ZonedDateTime at, Map<String, Object> extras) {}
