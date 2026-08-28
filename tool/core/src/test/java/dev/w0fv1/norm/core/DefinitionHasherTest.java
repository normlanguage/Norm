package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

final class DefinitionHasherTest {
  private static final String GOLDEN_HASH =
      "8264de6d27baf243186970bd4a33b9a5ae7b5ab5ff0ba287fd0b5715ea2ff0d6";

  @Test
  void hashesTheVersionedLengthDelimitedGroupPreimage() {
    DefinitionGroupId group = DefinitionHasher.hashGroup(new byte[] {0, 1, 2, (byte) 0xff});

    assertEquals(GOLDEN_HASH, group.toString());
    assertEquals(CoreSchemaVersion.V11, CoreIdentityVersion.CURRENT.schema());
    assertEquals(LanguageSemanticsVersion.V11, CoreIdentityVersion.CURRENT.semantics());
  }

  @Test
  void hashesContentDeterministicallyAndDistinguishesPayloads() {
    byte[] payload = {1, 2, 3};
    DefinitionGroupId first = DefinitionHasher.hashGroup(payload);
    payload[0] = 9;

    assertEquals(first, DefinitionHasher.hashGroup(new byte[] {1, 2, 3}));
    assertNotEquals(first, DefinitionHasher.hashGroup(payload));
    assertThrows(NullPointerException.class, () -> DefinitionHasher.hashGroup(null));
  }

  @Test
  void ignoresDefaultLocaleAndTimeZone() {
    Locale originalLocale = Locale.getDefault();
    TimeZone originalTimeZone = TimeZone.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("ar-EG"));
      TimeZone.setDefault(TimeZone.getTimeZone("America/St_Johns"));

      assertEquals(
          GOLDEN_HASH, DefinitionHasher.hashGroup(new byte[] {0, 1, 2, (byte) 0xff}).toString());
    } finally {
      Locale.setDefault(originalLocale);
      TimeZone.setDefault(originalTimeZone);
    }
  }
}
