package io.temporal.devdays.essentials.timeouts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import io.temporal.devdays.essentials.deck.Slides;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Every TimeoutSequence slide must step through exactly its scenario's captions.
 *
 * <p>A slide's {@code clicks:} sets how many times the presenter can advance it. If it is lower than
 * the scenario's captions, the last ones are unreachable; if higher, the diagram sits frozen on its
 * final caption. Nothing in Slidev checks this, so this does, reading
 * components/timeout-scenarios.ts as text.
 */
class TimeoutScenariosTest {
  private static final Path SCENARIOS =
      Slides.SNIPPETS.resolve("../components/timeout-scenarios.ts").normalize();
  private static final Pattern SCENARIO = Pattern.compile("^  '?([\\w-]+)'?: \\{$");
  private static final Pattern SLIDE_SPLIT =
      Pattern.compile("^---\\n(?=layout:|src:)", Pattern.MULTILINE);
  private static final Pattern USE = Pattern.compile("<TimeoutSequence scenario=\"([\\w-]+)\"");
  private static final Pattern CLICKS = Pattern.compile("^clicks: (\\d+)$", Pattern.MULTILINE);

  /** Scenario name to its number of {@code say} captions. */
  static Map<String, Integer> captions() {
    Map<String, Integer> found = new LinkedHashMap<>();
    String current = null;
    boolean inSay = false;
    for (String line : Slides.read(SCENARIOS).lines().toList()) {
      Matcher m = SCENARIO.matcher(line);
      if (m.matches()) {
        current = m.group(1);
        found.put(current, 0);
      } else if (current != null && line.equals("    say: [")) {
        inSay = true;
      } else if (inSay && line.equals("    ],")) {
        inSay = false;
      } else if (inSay && line.startsWith("      '")) {
        found.merge(current, 1, Integer::sum);
      }
    }
    return found;
  }

  record Use(String segment, String scenario, int clicks) {}

  static List<Use> uses() {
    List<Use> found = new ArrayList<>();
    for (Path segment : Slides.segmentFiles()) {
      for (String slide : SLIDE_SPLIT.split(Slides.read(segment))) {
        Matcher m = USE.matcher(slide);
        if (!m.find()) continue;
        Matcher c = CLICKS.matcher(slide);
        found.add(
            new Use(
                segment.getFileName().toString(),
                m.group(1),
                c.find() ? Integer.parseInt(c.group(1)) : -1));
      }
    }
    return found;
  }

  @Test
  void everyScenarioIsOnASlide() {
    Map<String, Integer> captions = captions();
    assertTrue(captions.size() >= 7, "expected at least 7 scenarios in " + SCENARIOS);
    assertEquals(
        new TreeSet<>(captions.keySet()),
        new TreeSet<>(uses().stream().map(Use::scenario).toList()),
        "every scenario in timeout-scenarios.ts should be on a slide, and every slide's scenario"
            + " should exist");
  }

  @TestFactory
  Stream<DynamicTest> everySlideSetsClicksToItsScenariosSteps() {
    Map<String, Integer> captions = captions();
    return uses().stream()
        .map(
            u ->
                dynamicTest(
                    u.segment() + ":" + u.scenario(),
                    () -> {
                      Integer say = captions.get(u.scenario());
                      assertTrue(say != null, u.segment() + " uses unknown scenario " + u.scenario());
                      assertEquals(
                          say - 1,
                          u.clicks(),
                          u.segment()
                              + ": clicks: "
                              + u.clicks()
                              + ", but "
                              + u.scenario()
                              + " has "
                              + say
                              + " captions");
                    }));
  }
}
