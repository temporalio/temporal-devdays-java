package io.temporal.devdays.essentials.patterns4;

import java.util.ArrayList;
import java.util.List;

/** An in-memory users table, so a test can count the rows. Not on a slide. */
public class UserDb {
  public final List<User> rows = new ArrayList<>();

  public synchronized User addUser(UserData data) {
    User user = new User(rows.size() + 1, data.email());
    rows.add(user);
    return user;
  }
}
