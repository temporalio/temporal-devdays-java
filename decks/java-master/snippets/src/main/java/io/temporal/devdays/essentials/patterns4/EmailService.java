package io.temporal.devdays.essentials.patterns4;

/** The outside world's mail server. Tests pass a fake that can fail. Not on a slide. */
public interface EmailService {
  void send(String to, String body);

  default void sendWelcomeEmail(String to) {
    send(to, "Welcome!");
  }
}
