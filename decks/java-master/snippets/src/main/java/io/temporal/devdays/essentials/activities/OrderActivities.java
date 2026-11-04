package io.temporal.devdays.essentials.activities;

import io.temporal.activity.ActivityInterface;

// slide: 06-activities.md "heading: Java Activity interface"
// OrderActivities.java
@ActivityInterface
public interface OrderActivities {
  OrderResult submitOrder(Order order);
}
// end slide
