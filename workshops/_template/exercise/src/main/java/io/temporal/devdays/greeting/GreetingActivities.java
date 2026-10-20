package io.temporal.devdays.greeting;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface GreetingActivities {
    String composeGreeting(String name);
}
