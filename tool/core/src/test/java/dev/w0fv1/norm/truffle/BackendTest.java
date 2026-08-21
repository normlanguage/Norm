package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.execution.ProgramRunner;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.utils.BackendInfo;
import dev.w0fv1.norm.value.SourceFile;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

final class BackendTest {
  @Test
  void resolvesTheConfiguredTruffleRuntime() {
    assertFalse(BackendInfo.runtimeName().isBlank());
  }

  @Test
  void executesTypedPrintThroughATruffleCallTarget() {
    var compilation =
        new Compiler()
            .compile(
                SourceFile.of(Path.of("hello.norm"), "void main() { print(\"Hello from Norm\") }"));
    var output = new StringWriter();

    new ProgramRunner().run(compilation.program().orElseThrow(), new PrintWriter(output));

    assertEquals("Hello from Norm" + System.lineSeparator(), output.toString());
  }

  @Test
  void executesSourceThroughTheRegisteredPolyglotLanguage() {
    var output = new ByteArrayOutputStream();

    try (Context context = Context.newBuilder("norm").out(output).build()) {
      context.eval("norm", "void main() { print(\"Hello from Polyglot\") }");
    }

    assertEquals(
        "Hello from Polyglot" + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
  }
}
