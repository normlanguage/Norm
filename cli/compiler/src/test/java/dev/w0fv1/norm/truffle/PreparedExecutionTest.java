package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

final class PreparedExecutionTest {
  @Test
  void preparesOnceAndExecutesWithIndependentRuntimeContexts() {
    var artifact =
        NormTestKit.compile("Void main() { printLine(\"prepared\") }")
            .program()
            .orElseThrow()
            .compilation()
            .artifact();
    var backend = new TruffleExecutionBackend();
    var prepared = backend.prepare(artifact);
    assertEquals(1, backend.cachedArtifacts());
    for (int attempt = 0; attempt < 2; attempt++) {
      var output = new StringWriter();
      prepared.execute(ExecutionContext.of(new PrintWriter(output), JdkSystemPlatform.standard()));
      assertEquals("prepared" + System.lineSeparator(), output.toString());
      assertEquals(1, backend.cachedArtifacts());
    }
  }
}
