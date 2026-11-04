package io.temporal.devdays.essentials.workflows;

/** What the transfer Workflow returns. Not on a slide. */
public record Receipt(String status, String reason) {
  public static Receipt completed() {
    return new Receipt("completed", null);
  }

  public static Receipt rejected(String reason) {
    return new Receipt("rejected", reason);
  }

  public static Receipt refunded(String reason) {
    return new Receipt("refunded", reason);
  }
}
