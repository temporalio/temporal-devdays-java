package io.temporal.devdays.subscription;

import java.time.Duration;

/** Names shared by the Worker, the starter and the tests. */
public final class Shared {
    /** The Task Queue this Workshop's Worker polls and its starter targets. */
    public static final String TASK_QUEUE = "subscription-tasks";

    /** One billing cycle. Compressed so the history grows fast enough to watch. */
    public static final Duration CYCLE = Duration.ofSeconds(1);

    private Shared() {}
}
