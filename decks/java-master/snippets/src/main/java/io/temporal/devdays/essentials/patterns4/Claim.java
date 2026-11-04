package io.temporal.devdays.essentials.patterns4;

import java.util.List;

/** Passed between the 04-design-patterns Workflows and Activities. Not on a slide. */
public record Claim(String claimId, double amount, ClaimType type, List<String> history) {}
