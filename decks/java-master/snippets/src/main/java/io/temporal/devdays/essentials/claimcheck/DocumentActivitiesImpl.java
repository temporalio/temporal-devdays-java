package io.temporal.devdays.essentials.claimcheck;

/** The bytes live only inside these Activities and in the store. */
public class DocumentActivitiesImpl implements DocumentActivities {
  /** The size of every "downloaded" document: 3 MB, over the 2 MB payload limit. */
  public static final int DOCUMENT_BYTES = 3 * 1024 * 1024;

  private final ClaimCheckStorage storage;

  public DocumentActivitiesImpl(ClaimCheckStorage storage) {
    this.storage = storage;
  }

  @Override
  public String downloadDocument(String url) {
    byte[] document = new byte[DOCUMENT_BYTES]; // stands in for an HTTP download of url
    return storage.uploadToStorage(document);
  }

  @Override
  public String summarizeDocument(String key) {
    return storage.fetchFromStorage(key).length + " bytes";
  }

  @Override
  public void deleteDocument(String key) {
    storage.deleteFromStorage(key);
  }
}
