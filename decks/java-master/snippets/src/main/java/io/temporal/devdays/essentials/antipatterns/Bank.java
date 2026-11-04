package io.temporal.devdays.essentials.antipatterns;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A stand-in for the bank's core system: records every deposit it releases. */
public class Bank {
  public final List<String> released = new CopyOnWriteArrayList<>();

  public void release(String depositId) {
    released.add(depositId);
  }
}
