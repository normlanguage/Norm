package dev.w0fv1.norm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.testing.NormTestKit;
import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class NativeApplicationProgramTest {
  @Test
  void doesNotRetainBuildBindingDescriptions() {
    for (var component : NativeApplicationProgram.class.getRecordComponents()) {
      org.junit.jupiter.api.Assertions.assertFalse(
          component.getGenericType().getTypeName().contains("LinkedJarBinding"),
          component.getName());
    }
  }

  @Test
  void executesPreparedCodeWithIndependentContexts() {
    var artifact =
        NormTestKit.compile("Void main() { printLine(\"native\") }")
            .program()
            .orElseThrow()
            .compilation()
            .artifact();
    var program =
        new NativeApplicationProgram(
            new TruffleExecutionBackend().prepare(artifact),
            dev.w0fv1.norm.jvm.JvmJarBindingRuntime.prepareCalls(Map.of(), Map.of()),
            "sample",
            dev.w0fv1.norm.jvm.LinkedJavaClasses.resolve(List.of(), getClass().getClassLoader()),
            Map.of());
    for (int attempt = 0; attempt < 2; attempt++) {
      var output = new StringWriter();
      program.execute(List.of(), new PrintWriter(output));
      assertEquals("native" + System.lineSeparator(), output.toString());
    }
  }

  @Test
  void preservesGuestFailureLocation() {
    var artifact =
        NormTestKit.compile("Void main() {\n  printLine(1 / 0)\n}")
            .program()
            .orElseThrow()
            .compilation()
            .artifact();
    var program =
        new NativeApplicationProgram(
            new TruffleExecutionBackend().prepare(artifact),
            dev.w0fv1.norm.jvm.JvmJarBindingRuntime.prepareCalls(Map.of(), Map.of()),
            "sample",
            dev.w0fv1.norm.jvm.LinkedJavaClasses.resolve(List.of(), getClass().getClassLoader()),
            Map.of());
    var failure =
        assertThrows(
            NormExecutionException.class,
            () -> program.execute(List.of(), new PrintWriter(new StringWriter())));
    assertEquals(2, failure.line());
  }
}
