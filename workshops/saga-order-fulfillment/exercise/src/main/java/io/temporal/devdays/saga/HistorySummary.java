package io.temporal.devdays.saga;

import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.common.WorkflowExecutionHistory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What Temporal recorded for one closed order: which Activities completed and
 * which failed, read from the Workflow's Event History rather than from any
 * console output. The starter prints it, so the Client tab shows the outcome
 * without the Worker's ledger.
 *
 * @param stepsCompleted forward steps with an Activity Task Completed event, oldest first
 * @param stepFailed the forward step with an Activity Task Failed event, or null
 * @param undosCompleted undos with an Activity Task Completed event, in the order they ran
 * @param undosFailed undos with an Activity Task Failed event
 * @param undosOwed for an order that did not complete: undos of steps that landed,
 *     with no Activity Task Completed event, newest first
 */
public record HistorySummary(
        List<String> stepsCompleted,
        String stepFailed,
        List<String> undosCompleted,
        List<String> undosFailed,
        List<String> undosOwed) {

    /** Each forward step, and the undo it owes once its effect has landed. */
    private static final Map<String, String> UNDO_OF = new LinkedHashMap<>();

    static {
        UNDO_OF.put("ReserveInventory", "ReleaseInventoryIfReserved");
        UNDO_OF.put("ChargePayment", "RefundPaymentIfCharged");
        UNDO_OF.put("DispatchCourier", null);
    }

    public static HistorySummary of(WorkflowExecutionHistory history, Order order) {
        Map<Long, String> scheduled = new HashMap<>();
        List<String> completed = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        boolean orderCompleted = false;
        for (HistoryEvent event : history.getEvents()) {
            switch (event.getEventType()) {
                case EVENT_TYPE_ACTIVITY_TASK_SCHEDULED -> scheduled.put(event.getEventId(),
                        event.getActivityTaskScheduledEventAttributes().getActivityType().getName());
                case EVENT_TYPE_ACTIVITY_TASK_COMPLETED -> completed.add(
                        scheduled.get(event.getActivityTaskCompletedEventAttributes().getScheduledEventId()));
                case EVENT_TYPE_ACTIVITY_TASK_FAILED -> failed.add(
                        scheduled.get(event.getActivityTaskFailedEventAttributes().getScheduledEventId()));
                case EVENT_TYPE_ACTIVITY_TASK_TIMED_OUT -> failed.add(
                        scheduled.get(event.getActivityTaskTimedOutEventAttributes().getScheduledEventId()));
                case EVENT_TYPE_WORKFLOW_EXECUTION_COMPLETED -> orderCompleted = true;
                default -> {}
            }
        }

        List<String> stepsCompleted = completed.stream().filter(UNDO_OF::containsKey).toList();
        String stepFailed = failed.stream().filter(UNDO_OF::containsKey).findFirst().orElse(null);
        List<String> undosCompleted = completed.stream().filter(a -> !UNDO_OF.containsKey(a)).toList();
        List<String> undosFailed = failed.stream().filter(a -> !UNDO_OF.containsKey(a)).toList();

        // A completed order owes no undo. Otherwise a step landed if it completed,
        // or if it is the charge that captured the money and then reported failure
        // (--charge-times-out). The history alone cannot show that second case: the
        // Activity says Failed either way.
        List<String> landed = new ArrayList<>(orderCompleted ? List.of() : stepsCompleted);
        if ("ChargePayment".equals(stepFailed) && order.chargeFailsAfterEffect()) {
            landed.add(stepFailed);
        }
        // Newest first, the order the undos should run in.
        Collections.reverse(landed);
        List<String> undosOwed = landed.stream()
                .map(UNDO_OF::get)
                .filter(undo -> undo != null && !undosCompleted.contains(undo))
                .toList();
        return new HistorySummary(stepsCompleted, stepFailed, undosCompleted, undosFailed, undosOwed);
    }

    /** The summary as the starter prints it. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("  steps completed: " + list(stepsCompleted));
        if (stepFailed != null) {
            lines.add("  step failed:     " + stepFailed);
        }
        lines.add("  undos completed: " + list(undosCompleted));
        if (!undosFailed.isEmpty()) {
            lines.add("  undos failed:    " + list(undosFailed));
        }
        lines.add("  undos still owed: " + list(undosOwed));
        return lines;
    }

    private static String list(List<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
    }
}
