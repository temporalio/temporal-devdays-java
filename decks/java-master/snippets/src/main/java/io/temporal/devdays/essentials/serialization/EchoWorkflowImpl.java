package io.temporal.devdays.essentials.serialization;

public class EchoWorkflowImpl implements EchoWorkflow {
  @Override
  public Visit echo(Visit visit) {
    return visit;
  }
}
