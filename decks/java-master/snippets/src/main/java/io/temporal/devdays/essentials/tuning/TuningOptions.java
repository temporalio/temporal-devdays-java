package io.temporal.devdays.essentials.tuning;

import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.worker.WorkerOptions;
import io.temporal.worker.tuning.CompositeTuner;
import io.temporal.worker.tuning.FixedSizeSlotSupplier;
import io.temporal.worker.tuning.PollerBehaviorAutoscaling;
import io.temporal.worker.tuning.PollerBehaviorSimpleMaximum;
import io.temporal.worker.tuning.ResourceBasedController;
import io.temporal.worker.tuning.ResourceBasedControllerOptions;
import io.temporal.worker.tuning.ResourceBasedSlotOptions;
import io.temporal.worker.tuning.ResourceBasedSlotSupplier;
import io.temporal.worker.tuning.WorkerTuner;

/**
 * Every Worker option the tuning slides and notes name, compiled against the SDK: if one is renamed
 * or removed, the build fails here before the slide lies. {@code TuningTest} checks the defaults
 * the tables give.
 */
public final class TuningOptions {
  private TuningOptions() {}

  /** Compute and IO settings (fixed slots). */
  public static WorkerOptions fixedSlots() {
    return WorkerOptions.newBuilder()
        .setMaxConcurrentWorkflowTaskExecutionSize(200)
        .setMaxConcurrentActivityExecutionSize(200)
        .setMaxConcurrentLocalActivityExecutionSize(200)
        .setWorkflowTaskPollersBehavior(new PollerBehaviorSimpleMaximum(5))
        .setActivityTaskPollersBehavior(new PollerBehaviorAutoscaling())
        .setMaxTaskQueueActivitiesPerSecond(50)
        .setMaxWorkerActivitiesPerSecond(10)
        .setDefaultDeadlockDetectionTimeout(1000)
        .build();
  }

  /** The memory settings: the sticky cache and the Workflow threads, per factory. */
  public static WorkerFactoryOptions memory() {
    return WorkerFactoryOptions.newBuilder()
        .setWorkflowCacheSize(600)
        .setMaxWorkflowThreadCount(600)
        .setUsingVirtualWorkflowThreads(false)
        .build();
  }

  /** The composite tuner from the auto-tuning notes: fixed for Workflows, resource-based for Activities. */
  public static WorkerTuner composite() {
    ResourceBasedController controller =
        ResourceBasedController.newSystemInfoController(
            ResourceBasedControllerOptions.newBuilder(0.8, 0.8).build());
    return new CompositeTuner(
        new FixedSizeSlotSupplier<>(200),
        ResourceBasedSlotSupplier.createForActivity(
            controller, ResourceBasedSlotOptions.newBuilder().build()),
        new FixedSizeSlotSupplier<>(200),
        new FixedSizeSlotSupplier<>(200));
  }
}
