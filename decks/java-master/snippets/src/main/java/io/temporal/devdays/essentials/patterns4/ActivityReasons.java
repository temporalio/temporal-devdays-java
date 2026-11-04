package io.temporal.devdays.essentials.patterns4;

import static java.net.http.HttpResponse.BodyHandlers.ofString;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.sql.Connection;
import java.util.UUID;

/**
 * The three reasons on the first design-patterns slide, as code that would run in an Activity.
 * Compiled, not run: it needs a real API and a real database.
 */
public class ActivityReasons {
  private static final String API = "https://api.example.com";

  private final HttpClient http = HttpClient.newHttpClient();
  private final Connection db;

  ActivityReasons(Connection db) {
    this.db = db;
  }

  private static HttpRequest get(String url) {
    return HttpRequest.newBuilder(URI.create(url)).build();
  }

  void examples(RfiRequest req) throws Exception {
    // slide: 04-design-patterns.md "Make it an Activity if it…"
    // Talks to external systems
    var project = http.send(get(API + "/projects/" + req.projectId()), ofString());

    // Does I/O
    var query = db.prepareStatement("SELECT * FROM reviewer_history WHERE project_id = ?");
    query.setString(1, req.projectId());
    query.executeQuery();

    // Uses non-deterministic operations
    UUID.randomUUID(); // ❌ in a Workflow
    System.getenv("PRICING_TIER"); // ❌ in a Workflow
    // end slide
  }
}
