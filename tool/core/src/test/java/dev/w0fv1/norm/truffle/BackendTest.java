package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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
                SourceFile.of(
                    Path.of("hello.norm"), "Void main() { printLine(\"Hello from Norm\") }"));
    var output = new StringWriter();

    new ProgramRunner().run(compilation.program().orElseThrow(), new PrintWriter(output));

    assertEquals("Hello from Norm" + System.lineSeparator(), output.toString());
  }

  @Test
  void reusesContextIndependentArtifactsAcrossExecutions() {
    var program =
        new Compiler()
            .compile(SourceFile.of(Path.of("cached.norm"), "Void main() { printLine(\"cached\") }"))
            .program()
            .orElseThrow();
    TruffleExecutionBackend backend = new TruffleExecutionBackend();
    ProgramRunner runner = new ProgramRunner(backend);
    StringWriter first = new StringWriter();
    StringWriter second = new StringWriter();

    runner.run(program, new PrintWriter(first));
    runner.run(program, new PrintWriter(second));

    assertEquals("cached" + System.lineSeparator(), first.toString());
    assertEquals(first.toString(), second.toString());
    assertEquals(1, backend.cachedArtifacts());
  }

  @Test
  void sharesOneArtifactAcrossConcurrentExecutionContexts() {
    var program =
        new Compiler()
            .compile(
                SourceFile.of(
                    Path.of("concurrent.norm"), "Void main() { printLine(\"concurrent\") }"))
            .program()
            .orElseThrow();
    TruffleExecutionBackend backend = new TruffleExecutionBackend();
    ProgramRunner runner = new ProgramRunner(backend);

    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
      var executions =
          java.util.stream.IntStream.range(0, 32)
              .mapToObj(
                  ignored ->
                      java.util.concurrent.CompletableFuture.supplyAsync(
                          () -> {
                            StringWriter output = new StringWriter();
                            runner.run(program, new PrintWriter(output));
                            return output.toString();
                          },
                          executor))
              .toList();
      String expected = "concurrent" + System.lineSeparator();
      executions.forEach(execution -> assertEquals(expected, execution.join()));
    }

    assertEquals(1, backend.cachedArtifacts());
  }

  @Test
  void boundsTheArtifactCacheForLongRunningSessions() {
    TruffleExecutionBackend backend = new TruffleExecutionBackend(2);
    ProgramRunner runner = new ProgramRunner(backend);

    for (int value = 0; value < 3; value++) {
      var program =
          new Compiler()
              .compile(
                  SourceFile.of(
                      Path.of("bounded-" + value + ".norm"),
                      "Void main() { printLine(" + value + ") }"))
              .program()
              .orElseThrow();
      runner.run(program, new PrintWriter(new StringWriter()));
    }

    assertEquals(2, backend.cachedArtifacts());
  }

  @Test
  void evictsTheLeastRecentlyUsedArtifact() {
    TruffleExecutionBackend backend = new TruffleExecutionBackend(2);
    var first =
        new Compiler()
            .compile(SourceFile.of(Path.of("first.norm"), "Void main() { printLine(1) }"))
            .program()
            .orElseThrow();
    var second =
        new Compiler()
            .compile(SourceFile.of(Path.of("second.norm"), "Void main() { printLine(2) }"))
            .program()
            .orElseThrow();
    var third =
        new Compiler()
            .compile(SourceFile.of(Path.of("third.norm"), "Void main() { printLine(3) }"))
            .program()
            .orElseThrow();

    ExecutableProgram firstArtifact = backend.compile(null, first.coreCompilation());
    ExecutableProgram secondArtifact = backend.compile(null, second.coreCompilation());
    assertSame(firstArtifact, backend.compile(null, first.coreCompilation()));
    backend.compile(null, third.coreCompilation());

    assertSame(firstArtifact, backend.compile(null, first.coreCompilation()));
    assertNotSame(secondArtifact, backend.compile(null, second.coreCompilation()));
  }

  @Test
  void executesSourceThroughTheRegisteredPolyglotLanguage() {
    var output = new ByteArrayOutputStream();

    try (Context context = Context.newBuilder("norm").out(output).build()) {
      context.eval("norm", "Void main() { printLine(\"Hello from Polyglot\") }");
    }

    assertEquals(
        "Hello from Polyglot" + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
  }

  @Test
  void usesTheActivePolyglotContextWhenAnEngineReusesCompiledSource() {
    Source source = Source.create("norm", "Void main() { printLine(\"shared engine\") }");
    ByteArrayOutputStream firstOutput = new ByteArrayOutputStream();
    ByteArrayOutputStream secondOutput = new ByteArrayOutputStream();

    try (org.graalvm.polyglot.Engine engine = org.graalvm.polyglot.Engine.create()) {
      try (Context context = Context.newBuilder("norm").engine(engine).out(firstOutput).build()) {
        context.eval(source);
      }
      try (Context context = Context.newBuilder("norm").engine(engine).out(secondOutput).build()) {
        context.eval(source);
      }
    }

    String expected = "shared engine" + System.lineSeparator();
    assertEquals(expected, firstOutput.toString(StandardCharsets.UTF_8));
    assertEquals(expected, secondOutput.toString(StandardCharsets.UTF_8));
  }

  @Test
  void exposesGuestLocationAndGuestCallStackThroughPolyglot() throws Exception {
    var uri = java.net.URI.create("file:///polyglot-runtime-error.norm");
    Source source =
        Source.newBuilder(
                "norm",
                "Integer fail() { return 1 / 0 }\nVoid main() { printLine(fail()) }",
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
