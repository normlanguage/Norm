package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

final class CanonicalWriterTest {
  private static final String GOLDEN_ENCODING =
      "010000000673616d706c65"
          + "0201"
          + "0301020304"
          + "040102030405060708"
          + "0500000006c3a9f09f9880"
          + "0600000002ff00";

  @Test
  void writesCanonicalTaggedBigEndianEncoding() {
    byte[] encoded =
        new CanonicalWriter()
            .writeTag("sample")
            .writeBoolean(true)
            .writeInt(0x01020304)
            .writeLong(0x0102030405060708L)
            .writeString("é😀")
            .writeBytes(new byte[] {(byte) 0xff, 0})
            .toByteArray();

    assertEquals(GOLDEN_ENCODING, HexFormat.of().formatHex(encoded));
  }

  @Test
  void encodesBothBooleanValuesCanonically() {
    byte[] encoded = new CanonicalWriter().writeBoolean(false).writeBoolean(true).toByteArray();

    assertArrayEquals(new byte[] {2, 0, 2, 1}, encoded);
  }

  @Test
  void encodesStringsAsStrictUtf8AndRejectsMalformedUtf16() {
    assertEquals(
        "0500000000",
        HexFormat.of().formatHex(new CanonicalWriter().writeString("").toByteArray()));
    assertThrows(IllegalArgumentException.class, () -> new CanonicalWriter().writeString("\ud800"));
    assertThrows(IllegalArgumentException.class, () -> new CanonicalWriter().writeTag("\udc00"));
    assertThrows(NullPointerException.class, () -> new CanonicalWriter().writeString(null));
    assertThrows(NullPointerException.class, () -> new CanonicalWriter().writeTag(null));
  }

  @Test
  void defensivelyCopiesByteInputsAndOutputs() {
    byte[] source = {1, 2, 3};
    CanonicalWriter writer = new CanonicalWriter().writeBytes(source);
    source[0] = 9;

    byte[] first = writer.toByteArray();
    first[5] = 8;

    assertEquals("0600000003010203", HexFormat.of().formatHex(writer.toByteArray()));
    assertThrows(NullPointerException.class, () -> new CanonicalWriter().writeBytes(null));
  }

  @Test
  void ignoresDefaultLocaleAndTimeZone() {
    Locale originalLocale = Locale.getDefault();
    TimeZone originalTimeZone = TimeZone.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Chatham"));

      byte[] encoded =
          new CanonicalWriter()
              .writeTag("sample")
              .writeBoolean(true)
              .writeInt(0x01020304)
              .writeLong(0x0102030405060708L)
              .writeString("é😀")
              .writeBytes(new byte[] {(byte) 0xff, 0})
              .toByteArray();

      assertEquals(GOLDEN_ENCODING, HexFormat.of().formatHex(encoded));
    } finally {
      Locale.setDefault(originalLocale);
      TimeZone.setDefault(originalTimeZone);
    }
  }
}
