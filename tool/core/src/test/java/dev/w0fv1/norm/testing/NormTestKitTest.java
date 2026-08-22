package dev.w0fv1.norm.testing;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NormTestKitTest {
  @TempDir Path temporaryDirectory;

  @Test
  void requiresExpectedOutputToBePrivate() throws Exception {
    Path test = temporaryDirectory.resolve("PublicOracle.norm");
    Files.writeString(test, "void main() { print(1) } void expectedOutput() { print(1) }");

    assertThrows(AssertionError.class, () -> NormTestKit.assertSelfContainedTest(test));
  }
}
