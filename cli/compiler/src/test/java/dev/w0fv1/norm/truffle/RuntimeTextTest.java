package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

final class RuntimeTextTest {
  @Test
  void distinguishesUnicodeCodePointsAndGraphemes() {
    String text = "A\uD83D\uDE00e\u0301";
    assertEquals(4, RuntimeText.codePointSize(text));
    assertEquals(3, RuntimeText.graphemeSize(text));
    assertEquals(List.of("A", "\uD83D\uDE00", "e\u0301"), RuntimeText.graphemes(text).values);
    assertEquals("e\u0301", RuntimeText.sliceGraphemes(text, 2, 3, null));
    assertEquals("\uD83D\uDE00e", RuntimeText.sliceCodePoints(text, 1, 3, null));
    assertThrows(NormGuestException.class, () -> RuntimeText.sliceGraphemes(text, 0, 4, null));
  }

  @Test
  void treatsSplitAndReplacementAsLiteralText() {
    assertEquals(List.of("a", "b", ""), RuntimeText.split("a.b.", ".", null).values);
    assertEquals("a$1b$1", RuntimeText.replace("a.b.", ".", "$1", null));
    assertThrows(NormGuestException.class, () -> RuntimeText.split("text", "", null));
  }
}
