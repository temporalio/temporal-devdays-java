package io.temporal.devdays.saga;

import io.temporal.failure.ApplicationFailure;

/** Names and helpers shared by the Worker, the Workflow, the starter and the tests. */
public final class Shared {
    /** The Task Queue this Workshop's Worker polls and its starter targets. */
    public static final String TASK_QUEUE = "fulfillment-tasks";

    private Shared() {}

    /**
     * The deepest cause of a failure, as "Type: message". A failed order arrives
     * wrapped (WorkflowFailedException, then ActivityFailure, then
     * ApplicationFailure), and only the innermost one says what went wrong.
     */
    public static String rootCause(Throwable failure) {
        Throwable t = failure;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof ApplicationFailure app) {
            return app.getType() + ": " + app.getOriginalMessage();
        }
        return t.getMessage();
    }
}
