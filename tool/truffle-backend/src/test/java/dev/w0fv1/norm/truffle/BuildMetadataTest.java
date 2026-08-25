package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.oracle.truffle.api.TruffleLanguage;
import dev.w0fv1.norm.value.BuildMetadata;
import org.junit.jupiter.api.Test;

final class BuildMetadataTest {
  @Test
  void truffleRegistrationUsesTheGeneratedBuildVersion() {
    String version = Language.class.getAnnotation(TruffleLanguage.Registration.class).version();

    assertFalse(BuildMetadata.VERSION.isBlank());
    assertEquals(BuildMetadata.VERSION, version);
  }
}
