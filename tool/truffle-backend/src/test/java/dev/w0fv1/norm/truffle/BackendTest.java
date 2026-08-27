package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.utils.BackendInfo;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BackendTest {
  @Test
  void resolvesTheConfiguredTruffleRuntime() {
    assertFalse(BackendInfo.runtimeName().isBlank());
  }

  @Test
  void executesTypedPrintThroughATruffleCallTarget() {
    var compilation =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("hello.norm"), "Void main() { printLine(\"Hello from Norm\") }"));
    var output = new StringWriter();

    new NormRuntime().run(compilation.program().orElseThrow(), new PrintWriter(output));

    assertEquals("Hello from Norm" + System.lineSeparator(), output.toString());
  }

  @Test
  void reusesContextIndependentArtifactsAcrossExecutions() {
    var program =
        new CompilerSession()
            .compile(SourceFile.of(Path.of("cached.norm"), "Void main() { printLine(\"cached\") }"))
            .program()
            .orElseThrow();
    TruffleExecutionBackend backend = new TruffleExecutionBackend();
    NormRuntime runner = new NormRuntime(backend);
    StringWriter first = new StringWriter();
    StringWriter second = new StringWriter();

    runner.run(program, new PrintWriter(first));
    runner.run(program, new PrintWriter(second));

    assertEquals("cached" + System.lineSeparator(), first.toString());
    assertEquals(first.toString(), second.toString());
    assertEquals(1, backend.cachedArtifacts());
  }

  @Test
  void reusesExecutableCodeAcrossDebugOnlySourceChanges() {
    var compact =
        new CompilerSession()
            .compile(SourceFile.of(Path.of("debug-cache.norm"), "Void main() { printLine(1 / 0) }"))
            .program()
            .orElseThrow();
    var formatted =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("debug-cache.norm"), "\n\nVoid main() {\n  printLine(1 / 0)\n}\n"))
            .program()
            .orElseThrow();
    TruffleExecutionBackend backend = new TruffleExecutionBackend();
    NormRuntime runner = new NormRuntime(backend);

    assertSame(
        backend.compile(null, compact.compilation().artifact()),
        backend.compile(null, formatted.compilation().artifact()));
    assertThrows(
        NormExecutionException.class,
        () -> runner.run(compact, new PrintWriter(new StringWriter())));
    NormExecutionException formattedFailure =
        assertThrows(
            NormExecutionException.class,
            () -> runner.run(formatted, new PrintWriter(new StringWriter())));

    assertEquals(4, formattedFailure.line());
    assertEquals(1, backend.cachedArtifacts());
  }

  @Test
  void createsAnnotationInstancesForEachExecution() {
    SourceFile source =
        SourceFile.of(
            Path.of("annotation-state.norm"),
            "package std.annotation public interface AnnotationTarget {} "
                + "public interface TypeTarget extends AnnotationTarget {} "
                + "public interface AnnotationRetention {} "
                + "public interface RuntimeRetention extends AnnotationRetention {} "
                + "annotation Label implements TypeTarget, RuntimeRetention { String text } "
                + "@Label(text: \"initial\") value Point {} "
                + "Void main() { Label? label = reflect<Point>().annotation<Label>() "
                + "printLine(label?.text ?? \"missing\") "
                + "if (label != null) { label.text = \"changed\" } }");
    var program =
        new CompilerSession()
            .compile(
                new CompilationRequest(
                    new CompilationUnitId(source.id().uri()),
                    CompilationScope.module(
                        new ModuleCoordinate("std", 1),
                        Map.of(source.id(), "annotation-state.norm")),
                    source.id(),
                    List.of(source),
                    Set.of()))
            .program()
            .orElseThrow();
    TruffleExecutionBackend backend = new TruffleExecutionBackend();
    NormRuntime runner = new NormRuntime(backend);
    StringWriter first = new StringWriter();
    StringWriter second = new StringWriter();

    runner.run(program, new PrintWriter(first));
    runner.run(program, new PrintWriter(second));

    String expected = "initial" + System.lineSeparator();
    assertEquals(expected, first.toString());
    assertEquals(expected, second.toString());
    assertEquals(1, backend.cachedArtifacts());
  }

  @Test
  void sharesOneArtifactAcrossConcurrentExecutionContexts() {
    var program =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("concurrent.norm"), "Void main() { printLine(\"concurrent\") }"))
            .program()
            .orElseThrow();
    TruffleExecutionBackend backend = new TruffleExecutionBackend();
    NormRuntime runner = new NormRuntime(backend);

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
    NormRuntime runner = new NormRuntime(backend);

    for (int value = 0; value < 3; value++) {
      var program =
          new CompilerSession()
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
        new CompilerSession()
            .compile(SourceFile.of(Path.of("first.norm"), "Void main() { printLine(1) }"))
            .program()
            .orElseThrow();
    var second =
        new CompilerSession()
            .compile(SourceFile.of(Path.of("second.norm"), "Void main() { printLine(2) }"))
            .program()
            .orElseThrow();
    var third =
        new CompilerSession()
            .compile(SourceFile.of(Path.of("third.norm"), "Void main() { printLine(3) }"))
            .program()
            .orElseThrow();

    ExecutableProgram firstArtifact = backend.compile(null, first.compilation().artifact());
    ExecutableProgram secondArtifact = backend.compile(null, second.compilation().artifact());
    assertSame(firstArtifact, backend.compile(null, first.compilation().artifact()));
    backend.compile(null, third.compilation().artifact());

    assertSame(firstArtifact, backend.compile(null, first.compilation().artifact()));
    assertNotSame(secondArtifact, backend.compile(null, second.compilation().artifact()));
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
  void executesAFileBackedProjectThroughTheRegisteredPolyglotLanguage(@TempDir Path root)
      throws Exception {
    Path moduleRoot = java.nio.file.Files.createDirectories(root.resolve("sample"));
    Path libraryRoot = java.nio.file.Files.createDirectories(moduleRoot.resolve("library"));
    Path entry = moduleRoot.resolve("Main.norm");
    java.nio.file.Files.writeString(
        moduleRoot.resolve("module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"Main\", \"library.Value\"]) }");
    java.nio.file.Files.writeString(
        libraryRoot.resolve("Value.norm"),
        "package sample.library public Integer value() { return 42 }");
    String text = "package sample import sample.library.value Void main() { printLine(value()) }";
    java.nio.file.Files.writeString(entry, text);
    Source source = Source.newBuilder("norm", text, "Main.norm").uri(entry.toUri()).build();
    var output = new ByteArrayOutputStream();

    try (Context context = Context.newBuilder("norm").out(output).build()) {
      context.eval(source);
    }

    assertEquals("42" + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
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
