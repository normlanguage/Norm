package dev.w0fv1.norm.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SourceSpanTest {
  @Test
  void exposesTextAndPositions() {
    SourceFile source = SourceFile.of(Path.of("example.norm"), "first\nsecond");
    SourceSpan span = new SourceSpan(source, 6, 12);

    assertEquals("second", span.text());
    assertEquals(new SourcePosition(6, 2, 1), span.start());
    assertEquals(new SourcePosition(12, 2, 7), span.end());
  }

  @Test
  void combinesRangesFromTheSameSource() {
    SourceFile source = SourceFile.of(Path.of("example.norm"), "abcdef");
    SourceSpan covered = new SourceSpan(source, 1, 3).cover(new SourceSpan(source, 4, 6));

    assertEquals(new SourceSpan(source, 1, 6), covered);
    assertTrue(SourceSpan.at(source, 2).isEmpty());
  }

  @Test
  void rejectsInvalidAndCrossFileRanges() {
    SourceFile first = SourceFile.of(Path.of("first.norm"), "abc");
    SourceFile second = SourceFile.of(Path.of("second.norm"), "abc");

    assertThrows(IllegalArgumentException.class, () -> new SourceSpan(first, -1, 1));
    assertThrows(IllegalArgumentException.class, () -> new SourceSpan(first, 2, 1));
    assertThrows(IllegalArgumentException.class, () -> new SourceSpan(first, 0, 4));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SourceSpan(first, 0, 1).cover(new SourceSpan(second, 0, 1)));
  }
}
