package io.temporal.devdays.shipment;

/** The shipment to pack. */
public record ShipmentInput(String shipmentId, int itemCount) {}
