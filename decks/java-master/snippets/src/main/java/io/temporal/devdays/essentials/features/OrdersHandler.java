package io.temporal.devdays.essentials.features;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.temporal.aws.lambda.LambdaWorker;
import io.temporal.common.WorkerDeploymentVersion;

/*
 * The Serverless Workers slide in 22-more-features.md. Compiled against temporal-aws-lambda, never
 * run: it only makes sense inside AWS Lambda, invoked by Temporal.
 */
// slide: 22-more-features.md "heading: Serverless Workers in Java"
public class OrdersHandler implements RequestHandler<Object, Void> {
  private static final RequestHandler<Object, Void> WORKER =
      LambdaWorker.define(
          new WorkerDeploymentVersion("orders", "build-1"),
          builder -> {
            builder.setTaskQueue("orders");
            builder.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
            builder.registerActivitiesImplementations(new OrderActivitiesImpl());
          });

  @Override
  public Void handleRequest(Object input, Context context) {
    return WORKER.handleRequest(input, context);
  }
}
// end slide
