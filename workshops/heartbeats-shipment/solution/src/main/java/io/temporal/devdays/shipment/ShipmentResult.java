package io.temporal.devdays.shipment;

/**
 * What packing produced. finishedOnAttempt is the attempt that finished the job:
 * greater than 1 means a crash was survived.
 */
public record ShipmentResult(String shipmentId, int packed, int finishedOnAttempt) {}
