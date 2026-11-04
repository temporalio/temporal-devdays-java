package io.temporal.devdays.essentials.heartbeat;

/** How many items were packed, and on which attempt the Activity finished. */
public record ShipmentResult(String shipmentId, int itemsPacked, int attempt) {}
