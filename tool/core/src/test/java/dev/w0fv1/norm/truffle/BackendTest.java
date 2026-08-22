package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
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

  @Test
  void exposesGuestLocationAndGuestCallStackThroughPolyglot() throws Exception {
    var uri = java.net.URI.create("file:///polyglot-runtime-error.norm");
    Source source =
        Source.newBuilder(
                "norm",
                "int fail() { return 1 / 0 }\nvoid main() { print(fail()) }",
                "polyglot-runtime-error.norm")
            .uri(uri)
            .build();

    try (Context context = Context.newBuilder("norm").build()) {
      PolyglotException exception =
          assertThrows(PolyglotException.class, () -> context.eval(source));
      assertTrue(exception.isGuestException());
      assertEquals(uri, exception.getSourceLocation().getSource().getURI());
      assertEquals(1, exception.getSourceLocation().getStartLine());
      assertTrue(
          java.util.stream.StreamSupport.stream(
                      exception.getPolyglotStackTrace().spliterator(), false)
                  .filter(org.graalvm.polyglot.PolyglotException.StackFrame::isGuestFrame)
                  .count()
              >= 2);
    }
  }
}
