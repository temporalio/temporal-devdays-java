package io.temporal.devdays.essentials.patterns4;

import java.time.Instant;

/** Passed between the 04-design-patterns Workflows and Activities. Not on a slide. */
public record RfiRequest(
    String rfiId, String projectId, Instant submittedAt, long estimatedCost) {}
