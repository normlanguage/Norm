package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class ContentHashTest {
  private static final String ZERO_HASH = "0".repeat(64);
  private static final String MAX_HASH = "f".repeat(64);

  @Test
  void defensivelyCopiesInputAndOutput() {
    byte[] source = new byte[ContentHash.BYTE_LENGTH];
    source[0] = 1;
    ContentHash hash = ContentHash.of(source);

    source[0] = 2;
    byte[] exposed = hash.bytes();
    exposed[0] = 3;

    assertEquals("01" + "0".repeat(62), hash.hex());
    assertEquals(1, hash.bytes()[0]);
  }

  @Test
  void parsesHexAndUsesCanonicalLowercaseText() {
    String uppercase = "0123456789ABCDEF".repeat(4);
    ContentHash parsed = ContentHash.parse(uppercase);

    assertEquals("0123456789abcdef".repeat(4), parsed.hex());
    assertEquals(parsed.hex(), parsed.toString());
    assertEquals(parsed, ContentHash.of(parsed.bytes()));
    assertEquals(parsed.hashCode(), ContentHash.of(parsed.bytes()).hashCode());
  }

  @Test
  void rejectsInvalidBytesAndHex() {
    assertThrows(NullPointerException.class, () -> ContentHash.of(null));
    assertThrows(IllegalArgumentException.class, () -> ContentHash.of(new byte[31]));
    assertThrows(IllegalArgumentException.class, () -> ContentHash.of(new byte[33]));
    assertThrows(NullPointerException.class, () -> ContentHash.parse(null));
    assertThrows(IllegalArgumentException.class, () -> ContentHash.parse("0".repeat(63)));
    assertThrows(IllegalArgumentException.class, () -> ContentHash.parse("0".repeat(65)));
    assertThrows(IllegalArgumentException.class, () -> ContentHash.parse("g" + "0".repeat(63)));
    assertThrows(IllegalArgumentException.class, () -> ContentHash.parse(" " + "0".repeat(63)));
  }

  @Test
  void comparesUnsignedBytesLexicographically() {
    ContentHash zero = ContentHash.parse(ZERO_HASH);
    ContentHash highBit = ContentHash.parse("80" + "0".repeat(62));
    ContentHash maximum = ContentHash.parse(MAX_HASH);

    assertTrue(zero.compareTo(highBit) < 0);
    assertTrue(highBit.compareTo(maximum) < 0);
    assertNotEquals(zero, maximum);
    assertEquals(
        Arrays.asList(zero, highBit, maximum),
        Arrays.asList(maximum, zero, highBit).stream().sorted().toList());
  }
}
