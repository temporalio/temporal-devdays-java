package io.temporal.devdays.essentials.history;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.time.Duration;

/**
 * The pizza Workflow the ReplayFlow component on the "Event History and Replay" slide steps
 * through: a distance lookup, a 30-minute timer, a bill. The component's code panes are the {@code
 * pizza} and {@code getDistance} methods below, line for line.
 */
public final class Pizza {
  private Pizza() {}

  public record Order(String address) {}

  public record Bill(String address, int distance, int cents) {}

  /** The maps API getDistance calls. Tests pass a fake that counts calls. */
  public interface Maps {
    int distance(String from, String to);
  }

  @ActivityInterface
  public interface PizzaActivities {
    int getDistance(String address);

    Bill sendBill(Order order, int distance);
  }

  @WorkflowInterface
  public interface PizzaWorkflow {
    @WorkflowMethod
    Bill pizza(Order order);
  }

  public static class PizzaWorkflowImpl implements PizzaWorkflow {
    private final PizzaActivities activities =
        Workflow.newActivityStub(
            PizzaActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(10)).build());

    @Override
    public Bill pizza(Order order) {
      int distance = activities.getDistance(order.address());
      Workflow.sleep(Duration.ofMinutes(30));
      Bill bill = activities.sendBill(order, distance);
      return bill;
    }
  }

  public static class PizzaActivitiesImpl implements PizzaActivities {
    static final String STORE = "1 Main St";
    private final Maps maps;

    public PizzaActivitiesImpl(Maps maps) {
      this.maps = maps;
    }

    @Override
    public int getDistance(String address) {
      return maps.distance(STORE, address);
    }

    @Override
    public Bill sendBill(Order order, int distance) {
      return new Bill(order.address(), distance, 1000 + distance * 50);
    }
  }
}
