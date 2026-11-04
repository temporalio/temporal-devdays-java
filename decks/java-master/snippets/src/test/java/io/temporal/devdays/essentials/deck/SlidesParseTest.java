package io.temporal.devdays.essentials.deck;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Every Java block in the Deck parses, fragments included. */
class SlidesParseTest {

  private static final JavaParser PARSER =
      new JavaParser(new ParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_21));

  /**
   * A slide may show a whole file, a class cut off after its first members (the rest on the next
   * slide), a class's members, a method body, a single expression, or a {@code catch} clause on its
   * own. Try it at each level it could plausibly sit at. A line that is
   * only {@code ...} stands for elided code and is dropped first.
   */
  static boolean parses(String code) {
    String c = code.replaceAll("(?m)^[ \\t]*\\.\\.\\.[ \\t]*$", "");
    List<String> wrappers =
        List.of(
            c,
            c + "\n}",
            "class _C {\n" + c + "\n}",
            "class _C { void _f() throws Exception {\n" + c + "\n} }",
            "class _C { Object _o = \n" + c + "\n; }",
            "class _C { void _f() throws Exception {\ntry {}\n" + c + "\n} }");
    return wrappers.stream().anyMatch(w -> PARSER.parse(w).isSuccessful());
  }

  @Test
  void theDeckHasJavaBlocks() {
    assertTrue(Slides.allSlideBlocks().size() >= 25, "expected at least 25 java blocks");
  }

  @TestFactory
  Stream<DynamicTest> everyBlockParses() {
    return Slides.allSlideBlocks().stream()
        .map(
            b ->
                dynamicTest(
                    b.segment() + ":" + b.line(),
                    () ->
                        assertTrue(
                            parses(b.code()),
                            b.segment() + ":" + b.line() + " is not valid Java")));
  }
}
