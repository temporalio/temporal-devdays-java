package io.temporal.devdays.essentials.patterns4;

public class UserActivitiesImpl implements UserActivities {
  private final UserDb db;
  private final EmailService emailService;

  public UserActivitiesImpl(UserDb db, EmailService emailService) {
    this.db = db;
    this.emailService = emailService;
  }

  // slide: 04-design-patterns.md "When to split an Activity"
  // ❌ One Activity doing two things — not safe to retry
  @Override
  public User createUser(UserData data) {
    User user = db.addUser(data);
    emailService.sendWelcomeEmail(user.email());

    return user;
  }
  // end slide
}
