package io.temporal.devdays.essentials.encryption;

public class GreetWorkflowImpl implements GreetWorkflow {
  @Override
  public String greet(String name) {
    return "Hello, " + name;
  }
}
