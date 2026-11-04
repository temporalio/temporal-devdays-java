package io.temporal.devdays.essentials.serialization;

/** What the Workflow saw: the zone of the visit it was handed, and the visit itself. */
public record VisitCheck(String zone, Visit visit) {}
