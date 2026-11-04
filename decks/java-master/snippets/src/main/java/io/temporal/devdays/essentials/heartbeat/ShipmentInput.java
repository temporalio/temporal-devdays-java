package io.temporal.devdays.essentials.heartbeat;

/** The shipment to pack. Same shape as the Heartbeats Workshop. */
public record ShipmentInput(String shipmentId, int itemCount) {}
