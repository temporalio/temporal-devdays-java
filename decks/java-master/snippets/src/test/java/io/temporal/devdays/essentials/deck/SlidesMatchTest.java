package io.temporal.devdays.essentials.deck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** A slide and the snippet that runs its code must say exactly the same thing. */
class SlidesMatchTest {

  @Test
  void everySnippetMarksASlide() {
    assertTrue(Slides.regions().size() >= 13, "expected at least 13 // slide: regions in src/");
  }

  @TestFactory
  Stream<DynamicTest> everyRegionMatchesItsSlide() {
    return Slides.regions().stream()
        .map(
            r ->
                dynamicTest(
                    r.source() + " -> " + r.segment() + " " + r.anchor(),
                    () ->
                        assertEquals(
                            Slides.slideCode(r.segment(), r.anchor()),
                            r.code(),
                            r.source()
                                + " has drifted from "
                                + r.segment()
                                + " ("
                                + r.anchor()
                                + "). Edit the slide and the snippet together.")));
  }

  @TestFactory
  Stream<DynamicTest> everySplitSlideMatchesItsParts() {
    return Slides.partGroups().stream()
        .map(
            g ->
                dynamicTest(
                    g[0] + " " + g[1] + " matches its parts",
                    () -> {
                      List<String> parts = Slides.parts(g[0], g[1]);
                      assertFalse(parts.isEmpty());
                      assertEquals(
                          Slides.slideCode(g[0], g[1]),
                          String.join("\n\n", parts),
                          "the parts for "
                              + g[0]
                              + " ("
                              + g[1]
                              + ") have drifted from the slide. Edit them together.");
                    }));
  }
}
