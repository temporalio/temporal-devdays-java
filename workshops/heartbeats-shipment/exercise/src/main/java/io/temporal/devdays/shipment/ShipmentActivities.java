package io.temporal.devdays.shipment;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface ShipmentActivities {
    /** Packs every item in a shipment. Runs long enough for a crash to land mid-job. */
    ShipmentResult packShipment(ShipmentInput input);
}
