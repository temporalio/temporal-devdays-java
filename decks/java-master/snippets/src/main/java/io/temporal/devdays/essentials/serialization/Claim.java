package io.temporal.devdays.essentials.serialization;

// part: 19-serialization.md "Data serialization" 1
public record Claim(String patientId, String drugNdc, long billedAmountCents) {}
// end part
