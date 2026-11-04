package io.temporal.devdays.essentials.claimcheck;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface DocumentActivities {
  /** Downloads the document, stores it, and returns only its key. */
  String downloadDocument(String url);

  /** Fetches the document by key and summarizes it. */
  String summarizeDocument(String key);

  void deleteDocument(String key);
}
