package io.temporal.devdays.shipment;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.client.ActivityCompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Packs a shipment, one item at a time. */
public class ShipmentActivitiesImpl implements ShipmentActivities {
    private static final Logger log = LoggerFactory.getLogger(ShipmentActivitiesImpl.class);

    @Override
    public ShipmentResult packShipment(ShipmentInput input) {
        ActivityExecutionContext context = Activity.getExecutionContext();
        int attempt = context.getInfo().getAttempt();

        // TODO 1c start: start from the count the last attempt Heartbeated, or 0 on a first attempt
        int packed = context.getHeartbeatDetails(Integer.class).orElse(0);
        // TODO 1c end

        log.info("packing {}, attempt {}, starting at item {}", input.shipmentId(), attempt, packed);

        try {
            for (int item = packed; item < input.itemCount(); item++) {
                packOneItem();
                packed = item + 1;
                log.info("packed item {} of {}", packed, input.itemCount());

                // TODO 1b start: Heartbeat the number of items packed so far
                context.heartbeat(packed);
                // TODO 1b end
            }
        } catch (ActivityCompletionException e) {
            // Cancellation reaches a running Activity through its Heartbeat: the
            // next heartbeat() call throws. Let it out.
            log.info("interrupted at item {}", packed);
            throw e;
        }

        return new ShipmentResult(input.shipmentId(), packed, attempt);
    }

    private static void packOneItem() {
        try {
            Thread.sleep(Shared.MILLIS_PER_ITEM);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Activity.wrap(e);
        }
    }
}
