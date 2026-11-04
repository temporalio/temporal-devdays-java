package io.temporal.devdays.essentials.concepts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plain Java, free to touch the world. The Worker is handed one instance and calls it directly on
 * its Activity threads; {@link #CALLS} records what ran, for the runtime test.
 */
public class BankActivitiesImpl implements BankActivities {
  public static final List<String> CALLS = Collections.synchronizedList(new ArrayList<>());

  @Override
  public String withdraw(String transferId) {
    CALLS.add("withdraw " + transferId);
    return "withdrawn";
  }

  @Override
  public String deposit(String transferId) {
    CALLS.add("deposit " + transferId);
    return "deposited";
  }
}
