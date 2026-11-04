package io.temporal.devdays.essentials.activities;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.activity.Activity;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

// slide: 06-activities.md "heading: Java Activity implementation"
// OrderActivitiesImpl.java
public class OrderActivitiesImpl implements OrderActivities {
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json = new ObjectMapper();
  private final URI ordersApi;

  public OrderActivitiesImpl(URI ordersApi) {
    this.ordersApi = ordersApi;
  }

  @Override
  public OrderResult submitOrder(Order order) {
    try {
      HttpRequest request = HttpRequest.newBuilder(ordersApi.resolve("/submit"))
          .header("Content-Type", "application/json")
          .POST(BodyPublishers.ofString(json.writeValueAsString(order)))
          .build();
      HttpResponse<String> response = http.send(request, BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException("Orders API returned " + response.statusCode());
      }
      return json.readValue(response.body(), OrderResult.class);
    } catch (IOException | InterruptedException e) {
      throw Activity.wrap(e);
    }
  }
}
// end slide
