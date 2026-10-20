package io.temporal.devdays.shipment;

/** Names and sizes shared by the Worker, the starter and the tests. */
public final class Shared {
    /** Task Queue this Workshop's Worker polls and its starter targets. */
    public static final String TASK_QUEUE = "shipment-tasks";

    /** Items in one shipment. Big enough that a crash lands mid-pack. */
    public static final int BATCH_SIZE = 40;

    /** Simulated work per item. A whole shipment takes about 12 seconds. */
    public static final long MILLIS_PER_ITEM = 300;

    private Shared() {}
}
