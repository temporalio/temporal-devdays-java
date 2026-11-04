package io.temporal.devdays.essentials.serialization;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

/** Sends a Visit through the default converter and back, and shows what changed. */
public final class JsonTraps {
  private JsonTraps() {}

  public static Visit roundTrip(EchoWorkflow echo) {
    // slide: 19-serialization.md "What JSON does to your values"
    Visit received =
        echo.echo(
            new Visit(
                ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneId.of("Europe/Paris")),
                Map.of(
                    "copayCents", 1_500L,
                    "seenAt", Instant.parse("2026-01-01T09:30:00Z"))));

    received.at(); // 2026-01-01T09:00+01:00: the zone ID is gone
    received.extras().get("copayCents"); // 1500: an Integer now, not a Long
    received.extras().get("seenAt"); // "2026-01-01T09:30:00Z": a String
    // end slide
    return received;
  }
}
