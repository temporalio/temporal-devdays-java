package io.temporal.devdays.essentials.saga;

import java.util.ArrayList;
import java.util.List;

/**
 * What the downstream systems actually did, in order. In memory on purpose: it stands in for the
 * inventory service and the payment gateway. Only Activities touch it, never Workflow code.
 */
public final class Ledger {
  private static final List<String> ENTRIES = new ArrayList<>();

  private Ledger() {}

  public static synchronized void record(String entry) {
    ENTRIES.add(entry);
  }

  /** Every entry for one order, oldest first, e.g. [reserve:ORD-1, charge:ORD-1]. */
  public static synchronized List<String> entries(String orderId) {
    return ENTRIES.stream().filter(e -> e.endsWith(":" + orderId)).toList();
  }

  /** True while a reservation stands: reserved more times than released. */
  public static synchronized boolean isReserved(String orderId) {
    return count("reserve:" + orderId) > count("release:" + orderId);
  }

  /** True while a charge stands: charged more times than refunded. */
  public static synchronized boolean isCharged(String orderId) {
    return count("charge:" + orderId) > count("refund:" + orderId);
  }

  private static long count(String entry) {
    return ENTRIES.stream().filter(entry::equals).count();
  }
}
