package io.temporal.devdays.essentials.features;

import static io.temporal.api.enums.v1.ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP;

import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleHandle;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.SchedulePolicy;
import io.temporal.client.schedules.ScheduleSpec;
import java.util.List;

/** The Schedules slide in 22-more-features.md, in a method so the test can hand it a client. */
public final class DailyReportSchedule {
  private DailyReportSchedule() {}

  public static ScheduleHandle create(ScheduleClient scheduleClient) {
    // slide: 22-more-features.md "heading: Schedules in Java"
    ScheduleHandle schedule =
        scheduleClient.createSchedule(
            "daily-report",
            Schedule.newBuilder()
                .setAction(
                    ScheduleActionStartWorkflow.newBuilder()
                        .setWorkflowType(ReportWorkflow.class)
                        .setOptions(
                            WorkflowOptions.newBuilder()
                                .setWorkflowId("daily-report")
                                .setTaskQueue("reports")
                                .build())
                        .build())
                .setSpec(ScheduleSpec.newBuilder().setCronExpressions(List.of("0 9 * * *")).build())
                .setPolicy(
                    SchedulePolicy.newBuilder().setOverlap(SCHEDULE_OVERLAP_POLICY_SKIP).build())
                .build(),
            ScheduleOptions.newBuilder().build());
    // end slide
    return schedule;
  }
}
