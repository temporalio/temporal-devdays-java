package io.temporal.devdays.essentials.heartbeat;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ShipmentActivitiesImpl implements ShipmentActivities {
  /**
   * Every item packed, in order, across all attempts. The first attempt dies on item 2, so a
   * correct resume packs item 2 twice and never repeats 0 or 1.
   */
  public final List<Integer> packed = new CopyOnWriteArrayList<>();

  private void packItem(String shipmentId, int item) {
    packed.add(item);
    if (item == 2 && Activity.getExecutionContext().getInfo().getAttempt() == 1) {
      throw new IllegalStateException("the Worker died mid-shipment");
    }
  }

  // slide: 16-heartbeating.md "Heartbeats in Java"
  @Override
  public ShipmentResult packShipment(ShipmentInput input) {
    ActivityExecutionContext context = Activity.getExecutionContext();
    int attempt = context.getInfo().getAttempt();
    int start = context.getHeartbeatDetails(Integer.class).orElse(0);

    for (int item = start; item < input.itemCount(); item++) {
      packItem(input.shipmentId(), item);

      context.heartbeat(item + 1);
    }

    return new ShipmentResult(input.shipmentId(), input.itemCount(), attempt);
  }
  // end slide
}
