package io.temporal.devdays.childworkflows;

/** Names shared by the Worker, the starter and the tests. */
public final class Shared {
    /** The Task Queue this Workshop's Worker polls and its starter targets. */
    public static final String TASK_QUEUE = "child-workflows";

    private Shared() {}
}
