package io.temporal.devdays.essentials.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Every code-walkthrough bubble must point at the code it talks about.
 *
 * <p>A walkthrough pairs a {@code {all|3|5-6|...}} highlight sequence with a {@code notes:} list,
 * one bubble per step. Nothing in Slidev checks that the two agree, so an edit that shifts the code
 * by a line leaves every bubble aimed at the wrong line. These tests read the segments statically;
 * {@code scripts/check-walkthroughs.mjs} checks the rendered positions in a browser.
 */
class WalkthroughsTest {

  private static final Pattern SLIDE_SPLIT =
      Pattern.compile("^---\\n(?=layout:|src:)", Pattern.MULTILINE);
  private static final Pattern CODE_TAG = Pattern.compile("<code>(.*?)</code>");
  private static final Pattern HEADING = Pattern.compile("^heading: (.+)$", Pattern.MULTILINE);
  private static final Pattern FENCE =
      Pattern.compile("^```java \\{([^}]*)\\}\\n([\\s\\S]*?)^```$", Pattern.MULTILINE);

  record Walkthrough(String id, List<String> steps, List<String> notes, List<String> code) {}

  static List<Integer> parseRanges(String spec) {
    List<Integer> out = new ArrayList<>();
    for (String part : spec.split(",")) {
      String[] ab = part.trim().split("-");
      int a = Integer.parseInt(ab[0].trim());
      int b = ab.length > 1 ? Integer.parseInt(ab[1].trim()) : a;
      for (int i = a; i <= b; i++) out.add(i);
    }
    return out;
  }

  static List<String> parseNotes(String frontmatter) {
    List<String> notes = new ArrayList<>();
    String[] after = frontmatter.split("notes:", 2);
    if (after.length < 2) return notes;
    List<String> lines = after[1].lines().toList();
    for (String line : lines.subList(1, lines.size())) {
      if (!line.startsWith("  - ")) break;
      String note = line.substring(4).trim();
      if (note.length() >= 2 && note.startsWith("\"") && note.endsWith("\"")) {
        note = note.substring(1, note.length() - 1);
      }
      notes.add(note);
    }
    return notes;
  }

  static String unescape(String s) {
    return s.replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&");
  }

  static List<Walkthrough> walkthroughs() {
    List<Walkthrough> found = new ArrayList<>();
    for (Path segment : Slides.segmentFiles()) {
      String name = segment.getFileName().toString();
      for (String slide : SLIDE_SPLIT.split(Slides.read(segment))) {
        int sep = slide.indexOf("\n---\n");
        if (sep < 0) continue;
        String frontmatter = slide.substring(0, sep);
        String body = slide.substring(sep + 5);
        if (!frontmatter.contains("layout: code-walkthrough")) continue;
        Matcher h = HEADING.matcher(frontmatter);
        String heading = h.find() ? h.group(1).replaceAll("^\"|\"$", "") : "(no heading)";
        Matcher f = FENCE.matcher(body);
        if (!f.find()) {
          throw new IllegalStateException(name + ": " + heading + " has no highlighted java block");
        }
        List<String> specs = Arrays.asList(f.group(1).split("\\|"));
        // `all` burns click 0, unannotated.
        List<String> steps = specs.get(0).equals("all") ? specs.subList(1, specs.size()) : specs;
        List<String> code = Arrays.asList(f.group(2).split("\n", -1));
        code = code.subList(0, code.size() - 1);
        found.add(new Walkthrough(name + ": " + heading, steps, parseNotes(frontmatter), code));
      }
    }
    return found;
  }

  @Test
  void theDeckHasWalkthroughs() {
    assertTrue(walkthroughs().size() >= 15, "expected at least 15 code-walkthrough slides");
  }

  @TestFactory
  Stream<DynamicNode> everyBubblePointsAtItsCode() {
    return walkthroughs().stream().map(WalkthroughsTest::checks);
  }

  private static DynamicNode checks(Walkthrough w) {
    List<DynamicNode> nodes = new ArrayList<>();
    nodes.add(
        dynamicTest(
            "has one note per highlight step",
            () ->
                assertEquals(
                    w.steps().size(),
                    w.notes().size(),
                    w.steps().size()
                        + " highlight steps but "
                        + w.notes().size()
                        + " notes: each bubble would land on the wrong step")));
    IntStream.range(0, w.steps().size())
        .forEach(
            i -> {
              String spec = w.steps().get(i);
              List<Integer> lines = parseRanges(spec);
              String note = i < w.notes().size() ? w.notes().get(i) : "";
              List<String> lit =
                  lines.stream().map(n -> n <= w.code().size() ? w.code().get(n - 1) : "").toList();
              List<DynamicNode> step = new ArrayList<>();
              int max = lines.stream().mapToInt(Integer::intValue).max().orElse(0);
              step.add(
                  dynamicTest(
                      "is inside the code",
                      () ->
                          assertTrue(
                              max <= w.code().size(),
                              "highlights line " + max + " of " + w.code().size())));
              // A range that begins or ends on a blank line is the signature of code that moved
              // under a highlight: the bubble centres on empty space.
              step.add(
                  dynamicTest(
                      "does not start or end on a blank line",
                      () ->
                          assertTrue(
                              !lit.get(0).isBlank() && !lit.get(lit.size() - 1).isBlank(),
                              "lines " + spec + " start or end blank; did the code shift? Lit: " + lit)));
              Matcher m = CODE_TAG.matcher(note);
              List<String> named = new ArrayList<>();
              while (m.find()) named.add(m.group(1));
              if (!named.isEmpty()) {
                String joined = String.join("\n", lit);
                step.add(
                    dynamicTest(
                        "names only code on its lit lines",
                        () -> {
                          for (String n : named) {
                            // Match on the identifier, not the punctuation around it.
                            String token = unescape(n).replaceAll("[()=:;]+$", "");
                            assertTrue(
                                joined.contains(token),
                                "note mentions <code>" + n + "</code> but the lit lines are " + lit);
                          }
                        }));
              }
              nodes.add(dynamicContainer("step " + (i + 1), step));
            });
    return dynamicContainer(w.id(), nodes);
  }
}
