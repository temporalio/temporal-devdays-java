package io.temporal.devdays.essentials.deck;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads code out of the Deck's segments and out of the snippet sources.
 *
 * <p>A snippet marks the code a slide shows between {@code // slide: <segment.md> "<anchor>"} and
 * {@code // end slide}. The anchor is any text in the segment before the code block, usually the
 * walkthrough's {@code heading:}; the region must equal the first {@code java} block after it.
 *
 * <p>A slide that shows several files in one block (an interface, then its implementation) is
 * split across sources with {@code // part: <segment.md> "<anchor>" <n>} and {@code // end part};
 * the block must equal the parts in order, joined by one blank line.
 */
public final class Slides {
  private Slides() {}

  public static final Path SNIPPETS = Path.of("").toAbsolutePath();
  public static final Path SRC = SNIPPETS.resolve("src/main/java");
  public static final Path SEGMENTS = SNIPPETS.resolve("../segments").normalize();

  private static final Pattern MARKER =
      Pattern.compile("^([ \\t]*)// slide: (\\S+) \"([^\"]+)\"$");
  private static final Pattern PART =
      Pattern.compile("^([ \\t]*)// part: (\\S+) \"([^\"]+)\" (\\d+)$");

  /** Java code fences. Highlight specs and other options follow the language on the same line. */
  public static final Pattern FENCE =
      Pattern.compile("^```java\\b[^\\n]*\\n([\\s\\S]*?)^```$", Pattern.MULTILINE);

  public record Region(String source, String segment, String anchor, String code) {}

  public record Block(String segment, int line, String code) {}

  public static String read(Path p) {
    try {
      return Files.readString(p);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static List<Path> segmentFiles() {
    try (Stream<Path> s = Files.list(SEGMENTS)) {
      return s.filter(p -> p.toString().endsWith(".md")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static List<Path> sources() {
    try (Stream<Path> s = Files.walk(SRC)) {
      return s.filter(p -> p.toString().endsWith(".java"))
          .sorted(Comparator.comparing(Path::toString))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static String dedent(List<String> lines) {
    int cut =
        lines.stream()
            .filter(l -> !l.isBlank())
            .mapToInt(l -> l.length() - l.stripLeading().length())
            .min()
            .orElse(0);
    List<String> out = new ArrayList<>();
    for (String l : lines) out.add(l.length() >= cut ? l.substring(cut) : l.strip());
    return String.join("\n", out);
  }

  private static String rel(Path p) {
    return SNIPPETS.relativize(p).toString();
  }

  /** Every {@code // slide:} ... {@code // end slide} region in the snippet sources, dedented. */
  public static List<Region> regions() {
    List<Region> found = new ArrayList<>();
    for (Path file : sources()) {
      List<String> lines = read(file).lines().toList();
      for (int i = 0; i < lines.size(); i++) {
        Matcher m = MARKER.matcher(lines.get(i));
        if (!m.matches()) continue;
        int end = lines.subList(i, lines.size()).indexOf(m.group(1) + "// end slide");
        if (end < 0) {
          throw new IllegalStateException(
              rel(file) + ":" + (i + 1) + " has a slide marker with no // end slide");
        }
        found.add(
            new Region(
                rel(file), m.group(2), m.group(3), dedent(lines.subList(i + 1, i + end))));
      }
    }
    return found;
  }

  /** The first Java code block after {@code anchor} in a segment. */
  public static String slideCode(String segment, String anchor) {
    String text = read(SEGMENTS.resolve(segment));
    int start = text.indexOf(anchor);
    if (start < 0) throw new IllegalStateException(segment + " has no \"" + anchor + "\"");
    Matcher m = FENCE.matcher(text);
    if (!m.find(start)) {
      throw new IllegalStateException(segment + " has no java block after \"" + anchor + "\"");
    }
    return m.group(1).replaceAll("\\n+$", "");
  }

  /** Every Java block in the Deck, with its segment and line number. */
  public static List<Block> allSlideBlocks() {
    List<Block> blocks = new ArrayList<>();
    for (Path segment : segmentFiles()) {
      String text = read(segment);
      Matcher m = FENCE.matcher(text);
      while (m.find()) {
        int line = (int) text.substring(0, m.start()).lines().count() + 1;
        blocks.add(new Block(segment.getFileName().toString(), line, m.group(1)));
      }
    }
    return blocks;
  }

  /** The parts of one slide, in order, read out of every source. */
  public static List<String> parts(String segment, String anchor) {
    TreeMap<Integer, String> found = new TreeMap<>();
    for (Path file : sources()) {
      List<String> lines = read(file).lines().toList();
      for (int i = 0; i < lines.size(); i++) {
        Matcher m = PART.matcher(lines.get(i));
        if (!m.matches() || !m.group(2).equals(segment) || !m.group(3).equals(anchor)) continue;
        int end = lines.subList(i, lines.size()).indexOf(m.group(1) + "// end part");
        if (end < 0) {
          throw new IllegalStateException(
              rel(file) + ":" + (i + 1) + " has a part marker with no // end part");
        }
        found.put(Integer.parseInt(m.group(4)), dedent(lines.subList(i + 1, i + end)));
      }
    }
    return new ArrayList<>(found.values());
  }

  /** Every [segment, anchor] that has {@code // part:} markers anywhere in the sources. */
  public static List<String[]> partGroups() {
    TreeMap<String, String[]> seen = new TreeMap<>();
    for (Path file : sources()) {
      for (String line : read(file).lines().toList()) {
        Matcher m = PART.matcher(line);
        if (m.matches()) seen.put(m.group(2) + " " + m.group(3), new String[] {m.group(2), m.group(3)});
      }
    }
    return new ArrayList<>(seen.values());
  }
}
