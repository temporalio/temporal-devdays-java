package io.temporal.devdays.essentials.saga;

import io.temporal.failure.ApplicationFailure;

/** Helpers shared by the Workflow and the tests, as in the Saga Workshop. */
public final class Shared {
  private Shared() {}

  /**
   * The deepest cause of a failure, as "Type: message". A failed order arrives wrapped
   * (WorkflowFailedException, then ActivityFailure, then ApplicationFailure).
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
