package io.temporal.devdays.essentials.heartbeat;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface ShipmentActivities {
  /** Packs every item in a shipment, one at a time, Heartbeating as it goes. */
  ShipmentResult packShipment(ShipmentInput input);
}
