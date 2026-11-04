package io.temporal.devdays.essentials.versioning;

import io.temporal.common.VersioningBehavior;
import io.temporal.common.WorkerDeploymentVersion;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerDeploymentOptions;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerOptions;

/** A Worker that joins the "orders" Worker Deployment as build 2.0.0, pinning what it starts. */
public final class VersionedWorker {
  private VersionedWorker() {}

  public static Worker create(WorkerFactory factory) {
    // slide: 18-versioning.md "Worker Versioning"
    WorkerOptions options =
        WorkerOptions.newBuilder()
            .setDeploymentOptions(
                WorkerDeploymentOptions.newBuilder()
                    .setUseVersioning(true)
                    .setVersion(new WorkerDeploymentVersion("orders", "2.0.0"))
                    .setDefaultVersioningBehavior(VersioningBehavior.PINNED)
                    .build())
            .build();
    Worker worker = factory.newWorker("orders", options);
    // end slide
    worker.registerWorkflowImplementationTypes(MyWorkflowImpl.class);
    worker.registerActivitiesImplementations(new VersioningActivitiesImpl());
    return worker;
  }
}
