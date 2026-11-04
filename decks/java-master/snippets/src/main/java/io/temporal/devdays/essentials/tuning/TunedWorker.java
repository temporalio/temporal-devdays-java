package io.temporal.devdays.essentials.tuning;

import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;
import io.temporal.worker.tuning.ResourceBasedControllerOptions;
import io.temporal.worker.tuning.ResourceBasedTuner;

/** The auto-tuning slide: a Worker whose slots come from a resource-based tuner. */
public final class TunedWorker {
  private TunedWorker() {}

  public static Worker create(WorkerFactory factory) {
    // slide: 26-worker-tuning.md "Execution slot auto-tuning"
    Worker worker =
        factory.newWorker(
            "order-task-queue",
            WorkerOptions.newBuilder()
                .setWorkerTuner(
                    ResourceBasedTuner.newBuilder()
                        .setControllerOptions(
                            ResourceBasedControllerOptions.newBuilder(0.8, 0.8).build())
                        .build())
                .build());
    // end slide
    worker.registerWorkflowImplementationTypes(SubmitWorkflowImpl.class);
    worker.registerActivitiesImplementations(new SubmitWorkflowImpl.Activities());
    return worker;
  }
}
