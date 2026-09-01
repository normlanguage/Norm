package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JarBindingCapabilityTest {
  @TempDir Path temporaryDirectory;

  @Test
  void userSourcesCannotInvokeTheHostBindingIntrinsic() {
    SourceFile source =
        SourceFile.of(
            temporaryDirectory.resolve("Main.norm"),
            "Void main() { __jarInvoke1<String>(call: \"forged\", arg0: \"value\") }");

    try (CompilerSession compiler = new CompilerSession()) {
      var result = compiler.compile(source);

      assertFalse(result.isSuccess());
      assertTrue(
          result.diagnostics().stream()
              .anyMatch(diagnostic -> diagnostic.message().contains("__jarInvoke1")));
    }
  }
}
