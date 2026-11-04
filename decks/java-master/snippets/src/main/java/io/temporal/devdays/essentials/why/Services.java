package io.temporal.devdays.essentials.why;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * In-memory fakes for the three services behind "The Temporal version" in 01-why-temporal.md.
 * Tests set {@code failuresLeft} to make a service throw a set number of times, and read {@code
 * calls} to see how often it was called.
 */
public final class Services {
  private Services() {}

  public static final class FakeService {
    public final AtomicInteger failuresLeft = new AtomicInteger();
    public final AtomicInteger calls = new AtomicInteger();
    private final Function<String, String> result;

    FakeService(Function<String, String> result) {
      this.result = result;
    }

    String run(String orderId) {
      calls.incrementAndGet();
      if (failuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
        throw new IllegalStateException("service unavailable");
      }
      return result.apply(orderId);
    }

    public void reset() {
      failuresLeft.set(0);
      calls.set(0);
    }
  }

  public static final FakeService INVENTORY = new FakeService(id -> null);
  public static final FakeService PAYMENTS = new FakeService(id -> "ch-" + id);
  public static final FakeService SHIPPING = new FakeService(id -> "trk-" + id);

  public static final class Inventory {
    public void reserve(String orderId) {
      INVENTORY.run(orderId);
    }
  }

  public static final class Payments {
    public String charge(String orderId) {
      return PAYMENTS.run(orderId);
    }
  }

  public static final class Shipping {
    public String ship(String orderId, String chargeId) {
      return SHIPPING.run(orderId);
    }
  }

  public static final Inventory inventory = new Inventory();
  public static final Payments payments = new Payments();
  public static final Shipping shipping = new Shipping();
}
