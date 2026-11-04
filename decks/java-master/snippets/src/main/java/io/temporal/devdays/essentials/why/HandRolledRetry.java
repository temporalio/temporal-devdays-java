package io.temporal.devdays.essentials.why;

/**
 * The three hand-rolled slides from 01-why-temporal.md: the naive call, the retry loop, and the
 * backoff. Plain Java with no Temporal in it, which is the point of those slides.
 */
public class HandRolledRetry {

  /** Whatever fulfils an order. Any failure is an exception. */
  public interface FulfillmentService {
    FulfillmentResult fulfill(String orderId) throws Exception;
  }

  public record FulfillmentResult(String trackingNumber) {}

  private final FulfillmentService fulfillmentService;

  public HandRolledRetry(FulfillmentService fulfillmentService) {
    this.fulfillmentService = fulfillmentService;
  }

  public FulfillmentResult naive(String orderId) throws Exception {
    // slide: 01-why-temporal.md "The naive version"
    return fulfillmentService.fulfill(orderId);
    // end slide
  }

  public FulfillmentResult withRetry(String orderId) throws InterruptedException {
    // slide: 01-why-temporal.md "So you add a retry"
    for (int attempt = 0; attempt < 4; attempt++) {
      try {
        return fulfillmentService.fulfill(orderId);
      } catch (Exception e) {
        Thread.sleep(5000);
      }
    }
    // end slide
    // The compiler insists on something here. This is what usually gets written.
    return null;
  }

  // slide: 01-why-temporal.md "So you add real backoff"
  FulfillmentResult fulfillWithRetry(String orderId) throws InterruptedException {
    long delay = 1000;
    int maxRetries = 3;
    for (int attempt = 0; attempt < maxRetries; attempt++) {
      try {
        return fulfillmentService.fulfill(orderId);
      } catch (Exception e) {
        if (attempt < maxRetries - 1) {
          Thread.sleep(delay);
          delay = Math.min(delay * 2, 30_000);
        }
      }
    }
    throw new IllegalStateException("Failed after too many attempts");
  }
  // end slide
}
