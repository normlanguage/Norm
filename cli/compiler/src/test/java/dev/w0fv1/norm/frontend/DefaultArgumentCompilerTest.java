package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class DefaultArgumentCompilerTest {
  @Test
  void checksUnusedInterfaceDefaultsInTheirDeclaration() {
    var result =
        NormTestKit.compile(
            "interface Reading { Integer read(Integer value = \"invalid\") } Void main() {}");
    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.primarySpan().text().equals("\"invalid\"")));
  }

  @Test
  void constructorDefaultsCannotReadAnInstanceBeforeConstruction() {
    var result =
        NormTestKit.compile(
            "class Box { Integer value = 1 Box(Integer input = this.value) {} } Void main() {}");
    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.primarySpan().text().equals("this")));
  }
}
