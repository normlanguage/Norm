package dev.w0fv1.norm.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SourceFileTest {
  @Test
  void indexesUnixWindowsAndLegacyLineEndings() {
    SourceFile source = SourceFile.of(Path.of("mixed.norm"), "one\r\ntwo\nthree\rfour");

    assertEquals(4, source.lineCount());
    assertEquals("one", source.lineText(1));
    assertEquals("two", source.lineText(2));
    assertEquals("three", source.lineText(3));
    assertEquals("four", source.lineText(4));
    assertEquals(new SourcePosition(5, 2, 1), source.positionAt(5));
    assertEquals(new SourcePosition(15, 4, 1), source.positionAt(15));
  }

  @Test
  void representsTheEmptyLineAfterATrailingNewline() {
    SourceFile source = SourceFile.of(Path.of("trailing.norm"), "line\n");

    assertEquals(2, source.lineCount());
    assertEquals("", source.lineText(2));
    assertEquals(new SourcePosition(5, 2, 1), source.positionAt(source.length()));
  }

  @Test
  void rejectsPositionsOutsideTheSource() {
    SourceFile source = SourceFile.of(Path.of("small.norm"), "x");

    assertThrows(IllegalArgumentException.class, () -> source.positionAt(-1));
    assertThrows(IllegalArgumentException.class, () -> source.positionAt(2));
    assertThrows(IllegalArgumentException.class, () -> source.lineText(0));
    assertThrows(IllegalArgumentException.class, () -> source.lineText(2));
  }
}
