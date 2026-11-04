package io.temporal.devdays.essentials.tuning;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Speaker notes: Workflow code that holds its thread without yielding. The Worker's deadlock
 * detector fails the Workflow Task after one second; the Workflow stays open.
 */
@WorkflowInterface
public interface BusyWorkflow {
  @WorkflowMethod
  long spin(long millis);

  class Impl implements BusyWorkflow {
    @Override
    public long spin(long millis) {
      // Real wall-clock time, on purpose: this is the bug the notes describe.
      long end = System.nanoTime() + millis * 1_000_000;
      long spins = 0;
      while (System.nanoTime() < end) spins++;
      return spins;
    }
  }
}
