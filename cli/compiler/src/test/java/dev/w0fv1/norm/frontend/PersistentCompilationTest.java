package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PersistentCompilationTest {
  @TempDir Path directory;

  @Test
  void preservesDefaultArgumentOriginsWhenReusingCallers() throws Exception {
    Path file = directory.resolve("defaults.norm");
    String original =
        """
        Integer value(Integer input = 17) { return input }
        Integer stable() { return value() }
        Void main() { printLine(stable()) }
        """;
    try (var compiler = CompilerSession.persistent(directory.resolve("cache"))) {
      var result = compiler.compile(SourceFile.of(file, original));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
    }
    String edited = "\n" + original.replace("input = 17", "input  =   17");
    try (var compiler = CompilerSession.persistent(directory.resolve("cache"));
        var fresh = new CompilerSession()) {
      var result = compiler.compile(SourceFile.of(file, edited));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var output = result.output().orElseThrow();
      assertEquals(0, output.state().buildReport().convertedDefinitions());
      var expected = fresh.compile(SourceFile.of(file, edited)).output().orElseThrow();
      assertEquals(
          dev.w0fv1.norm.core.ArtifactId.forArtifact(expected.artifact(), "test"),
          dev.w0fv1.norm.core.ArtifactId.forArtifact(output.artifact(), "test"));
      assertEquals(
          expected.artifact().authoring().occurrences(),
          output.artifact().authoring().occurrences());
    }
  }

  @Test
  void preservesLocalAnnotationIndicesWhileReusingCore() throws Exception {
    Path file = directory.resolve("annotations.norm");
    String original =
        """
        package std.annotation
        public interface AnnotationTarget {}
        public interface LocalTarget extends AnnotationTarget {}
        public interface AnnotationRetention {}
        public interface BinaryRetention extends AnnotationRetention {}
        annotation Marker implements LocalTarget, BinaryRetention {}
        Integer stable(Integer input) { @Marker() Integer copy = input return copy }
        Void main() { printLine(stable(1)) }
        """;
    java.util.function.Function<String, dev.w0fv1.norm.value.CompilationRequest> request =
        text -> {
          var source = SourceFile.of(file, text);
          return new dev.w0fv1.norm.value.CompilationRequest(
              new dev.w0fv1.norm.value.CompilationUnitId(source.id().uri()),
              dev.w0fv1.norm.value.CompilationScope.module(
                  new dev.w0fv1.norm.value.ModuleCoordinate("std", 1),
                  java.util.Map.of(source.id(), "annotations.norm")),
              source.id(),
              java.util.List.of(source),
              java.util.Set.of());
        };
    try (var compiler = CompilerSession.persistent(directory.resolve("cache"))) {
      var result = compiler.compile(request.apply(original));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
    }
    String edited = "\n\n" + original.replace("stable(1)", "stable(2)");
    try (var compiler = CompilerSession.persistent(directory.resolve("cache"));
        var fresh = new CompilerSession()) {
      var result = compiler.compile(request.apply(edited));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var output = result.output().orElseThrow();
      assertEquals(1, output.state().buildReport().convertedDefinitions());
      assertTrue(
          output.artifact().metadata().annotations().stream()
              .anyMatch(
                  annotation ->
                      annotation.target()
                          instanceof dev.w0fv1.norm.core.CoreAnnotationTarget.Local));
      var expected = fresh.compile(request.apply(edited)).output().orElseThrow();
      assertEquals(
          dev.w0fv1.norm.core.ArtifactId.forArtifact(expected.artifact(), "test"),
          dev.w0fv1.norm.core.ArtifactId.forArtifact(output.artifact(), "test"));
    }
  }

  @Test
  void reusesCoreAcrossSessionsWithoutInvalidatingIdenticalIndependentFunctions() throws Exception {
    Path file = directory.resolve("main.norm");
    String original =
        """
        Integer first() { return 1 }
        Integer second() { return 1 }
        Integer callFirst() { return first() }
        Integer callSecond() { return second() }
        Void main() { printLine(callFirst()) printLine(callSecond()) }
        """;
    try (var compiler = CompilerSession.persistent(directory.resolve("cache"))) {
      assertTrue(compiler.compile(SourceFile.of(file, original)).isSuccess());
    }
    String edited = "\n\n" + original.replace("first() { return 1", "first() { return 2");
    try (var compiler = CompilerSession.persistent(directory.resolve("cache"));
        var fresh = new CompilerSession()) {
      var result = compiler.compile(SourceFile.of(file, edited));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var output = result.output().orElseThrow();
      assertEquals(1, output.state().buildReport().convertedDefinitions());
      assertEquals(2, output.state().buildReport().relinkedDefinitions());
      assertEquals(2, output.state().buildReport().reusedDefinitions());
      assertEquals(3, output.state().buildReport().canonicalization().components());
      var expected = fresh.compile(SourceFile.of(file, edited)).output().orElseThrow();
      assertEquals(
          dev.w0fv1.norm.core.ArtifactId.forArtifact(expected.artifact(), "test"),
          dev.w0fv1.norm.core.ArtifactId.forArtifact(output.artifact(), "test"));
      for (var occurrence : output.artifact().authoring().occurrences()) {
        assertEquals(edited, occurrence.origin().rootSpan().source().text());
        occurrence
            .origin()
            .nodeSpans()
            .values()
            .forEach(span -> assertEquals(edited, span.source().text()));
      }
    }
  }

  @Test
  void keepsAuthoringSnapshotsIndependentOfPersistentCompilationArtifacts() throws Exception {
    Path cache = directory.resolve("cache");
    var source = SourceFile.of(directory.resolve("main.norm"), "Void main() {}");
    try (var compiler = CompilerSession.persistent(cache)) {
      var snapshot = compiler.snapshot(source);
      assertTrue(snapshot.document(source.id()).isPresent());
      assertTrue(snapshot.diagnostics().isEmpty());
    }
    try (var entries = java.nio.file.Files.walk(cache)) {
      assertEquals(0, entries.filter(java.nio.file.Files::isRegularFile).count());
    }
  }

  @Test
  void persistsReusableCompilationArtifactsWithoutWritingEveryDefinition() throws Exception {
    var text = new StringBuilder("Void main() {}\n");
    for (int index = 0; index < 100; index++)
      text.append("Integer value")
          .append(index)
          .append("() { return ")
          .append(index)
          .append(" }\n");
    Path cache = directory.resolve("cache");
    var source = SourceFile.of(directory.resolve("main.norm"), text.toString());
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(source).isSuccess());
    }
    try (var entries = java.nio.file.Files.walk(cache)) {
      assertTrue(
          entries.filter(java.nio.file.Files::isRegularFile).count() <= 4,
          "compilation must persist reusable artifacts without a second per-definition store");
    }
    try (var compiler = CompilerSession.persistent(cache)) {
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      assertEquals(
          0, result.output().orElseThrow().state().buildReport().canonicalization().components());
      assertTrue(result.output().orElseThrow().state().delta().added().isEmpty());
      assertEquals(
          101, result.output().orElseThrow().state().analysisReport().reusedDeclarations());
    }
  }

  @Test
  void restoresHistoryWithReleaseModuleEncapsulation() throws Exception {
    Path probe =
        Path.of(
            PersistentCompilationProbe.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    for (String phase : java.util.List.of("original", "edited", "callee-edited")) {
      Path output = directory.resolve("module-" + phase + ".txt");
      Process process =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                  "--sun-misc-unsafe-memory-access=allow",
                  "--module-path",
                  System.getProperty("norm.test.modulePath"),
                  "--patch-module",
                  "dev.w0fv1.norm=" + probe,
                  "--module",
                  "dev.w0fv1.norm/" + PersistentCompilationProbe.class.getName(),
                  directory.toString(),
                  phase)
              .redirectErrorStream(true)
              .redirectOutput(output.toFile())
              .start();
      try {
        assertTrue(
            process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS),
            "modular history probe timed out");
        assertEquals(0, process.exitValue(), java.nio.file.Files.readString(output));
      } finally {
        if (process.isAlive()) process.destroyForcibly();
      }
    }
  }

  @Test
  void reusesUnaffectedDeclarationsAfterOpeningANewCompilerSession() throws Exception {
    Path cache = directory.resolve("cache");
    Path path = directory.resolve("main.norm");
    String original =
        "Integer leaf() { return 1 }\nInteger caller() { return leaf() }\nInteger unrelated() { return 7 }\nVoid main() { var value = caller() }";
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(SourceFile.of(path, original)).isSuccess());
    }
    try (var compiler = CompilerSession.persistent(cache)) {
      var result = compiler.compile(SourceFile.of(path, original.replace("return 1", "return 2")));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var analysis = result.output().orElseThrow().state().analysisReport();
      assertEquals(1, analysis.analyzedDeclarations());
      assertEquals(3, analysis.reusedDeclarations());
    }
  }

  @Test
  void keepsIncrementalHistoryAfterAnExactPersistentResultHit() throws Exception {
    Path cache = directory.resolve("cache");
    Path path = directory.resolve("main.norm");
    String original = "Integer first() { return 1 }\nInteger second() { return 2 }\nVoid main() {}";
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(SourceFile.of(path, original)).isSuccess());
    }
    try (var compiler = CompilerSession.persistent(cache)) {
      var same = compiler.compile(SourceFile.of(path, original));
      assertEquals(0, same.output().orElseThrow().state().analysisReport().analyzedDeclarations());
      var edited = compiler.compile(SourceFile.of(path, original.replace("return 1", "return 3")));
      assertTrue(edited.isSuccess(), edited.diagnostics().toString());
      assertEquals(
          1, edited.output().orElseThrow().state().analysisReport().analyzedDeclarations());
      assertEquals(2, edited.output().orElseThrow().state().analysisReport().reusedDeclarations());
    }
  }

  @Test
  void reportsCurrentDiagnosticLocationsAfterCachedSuccessAndMovedErrors() throws Exception {
    Path cache = directory.resolve("cache");
    Path original = directory.resolve("main.norm");
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(SourceFile.of(original, "Void main() {} ")).isSuccess());
    }
    for (int padding = 0; padding < 3; padding++) {
      Path path = padding == 2 ? directory.resolve("moved.norm") : original;
      var source = SourceFile.of(path, "\n".repeat(padding) + "Void main() { missingName() }");
      try (var compiler = CompilerSession.persistent(cache)) {
        var result = compiler.compile(source);
        assertFalse(result.isSuccess());
        var diagnostic =
            result.diagnostics().stream()
                .filter(value -> value.primarySpan().text().contains("missingName"))
                .findFirst()
                .orElseThrow();
        assertEquals(source.id(), diagnostic.primarySpan().source().id());
        assertEquals(source.text().indexOf("missingName"), diagnostic.primarySpan().startOffset());
      }
    }
  }

  @Test
  void invalidatesAddedAndRemovedSourceDocuments() throws Exception {
    var main =
        SourceFile.of(directory.resolve("main.norm"), "Void main() { var value = helper() }");
    var helper = SourceFile.of(directory.resolve("helper.norm"), "Integer helper() { 42 }");
    var request =
        new dev.w0fv1.norm.value.CompilationRequest(main.id(), java.util.List.of(main, helper));
    Path cache = directory.resolve("cache");
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(request).isSuccess());
    }
    try (var compiler = CompilerSession.persistent(cache)) {
      var cached = compiler.compile(request);
      assertTrue(cached.isSuccess());
      assertEquals(
          0, cached.output().orElseThrow().state().analysisReport().analyzedDeclarations());
      assertFalse(compiler.compile(main).isSuccess());
    }
    var broken = SourceFile.of(directory.resolve("broken.norm"), "Void broken( {");
    try (var compiler = CompilerSession.persistent(cache)) {
      assertFalse(
          compiler
              .compile(
                  new dev.w0fv1.norm.value.CompilationRequest(
                      main.id(), java.util.List.of(main, helper, broken)))
              .isSuccess());
    }
  }

  @Test
  void includesPreludeContentAndSourceLocationInItsIdentity() throws Exception {
    var main = SourceFile.of(directory.resolve("main.norm"), "Void main() {}");
    var profile = LanguageProfile.withPrelude(ModuleBootstrap.prelude());
    Path cache = directory.resolve("cache");
    try (var compiler = CompilerSession.persistent(cache, LanguageProfile.kernel())) {
      assertTrue(compiler.compile(main).isSuccess());
    }
    try (var compiler = CompilerSession.persistent(cache, profile)) {
      var result = compiler.compile(main);
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      assertTrue(result.output().orElseThrow().state().analysisReport().analyzedDeclarations() > 0);
    }
    var first = new CompilationResultCache(cache.resolve("identity"), profile);
    assertNotEquals(
        first.key(dev.w0fv1.norm.value.CompilationRequest.single(main)),
        first.key(
            dev.w0fv1.norm.value.CompilationRequest.single(
                SourceFile.of(directory.resolve("renamed.norm"), main.text()))));
  }

  @Test
  void restoresGenericAndClosureFactsAtCurrentSourceLocations() throws Exception {
    Path cache = directory.resolve("cache");
    Path path = directory.resolve("main.norm");
    String original =
        "T identity<T>(T value) { return value }\n"
            + "Integer apply(Integer operation(Integer value)) { return operation(4) }\n"
            + "Integer stable() { return apply { value in identity(value) } }\n"
            + "Void main() { printLine(stable()) }";
    try (var compiler = CompilerSession.persistent(cache)) {
      var result = compiler.compile(SourceFile.of(path, original));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
    }
    var moved = SourceFile.of(path, "\n\n" + original.replace("return value", "return   value"));
    try (var compiler = CompilerSession.persistent(cache);
        var fresh = new CompilerSession()) {
      var restored = compiler.compile(moved);
      var expected = fresh.compile(moved);
      assertTrue(restored.isSuccess(), restored.diagnostics().toString());
      assertTrue(expected.isSuccess(), expected.diagnostics().toString());
      assertEquals(0, restored.output().orElseThrow().state().buildReport().convertedDefinitions());
      assertEquals(
          0, restored.output().orElseThrow().state().analysisReport().analyzedDeclarations());
      assertEquals(
          dev.w0fv1.norm.core.ArtifactId.forArtifact(
              expected.output().orElseThrow().artifact(), "test"),
          dev.w0fv1.norm.core.ArtifactId.forArtifact(
              restored.output().orElseThrow().artifact(), "test"));
    }
  }

  @Test
  void rebuildsWhenBothResultAndHistoryAreCorrupt() throws Exception {
    Path cache = directory.resolve("cache");
    var source = SourceFile.of(directory.resolve("main.norm"), "Void main() {}");
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(source).isSuccess());
    }
    try (var entries = java.nio.file.Files.walk(cache.resolve("compilations"))) {
      for (Path entry : entries.filter(path -> path.toString().endsWith(".bin")).toList())
        java.nio.file.Files.writeString(entry, "truncated");
    }
    try (var compiler = CompilerSession.persistent(cache)) {
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      assertEquals(
          1, result.output().orElseThrow().state().analysisReport().analyzedDeclarations());
    }
  }

  @Test
  void recompilesCorruptResultsAndUsesContentInsteadOfTimestamps() throws Exception {
    Path file = directory.resolve("main.norm");
    java.nio.file.Files.writeString(file, "Void main() {}");
    var timestamp = java.nio.file.Files.getLastModifiedTime(file);
    Path cache = directory.resolve("cache");
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(SourceFile.read(file)).isSuccess());
    }
    try (var entries = java.nio.file.Files.list(cache.resolve("compilations"))) {
      java.nio.file.Files.writeString(
          entries.filter(path -> path.toString().endsWith(".bin")).findFirst().orElseThrow(),
          "truncated");
    }
    try (var compiler = CompilerSession.persistent(cache)) {
      var result = compiler.compile(SourceFile.read(file));
      assertTrue(result.isSuccess());
      assertEquals(1, result.output().orElseThrow().state().analysisReport().reusedDeclarations());
    }
    java.nio.file.Files.writeString(file, "Void main() { missingName() }");
    java.nio.file.Files.setLastModifiedTime(file, timestamp);
    try (var compiler = CompilerSession.persistent(cache)) {
      assertFalse(compiler.compile(SourceFile.read(file)).isSuccess());
    }
  }

  @Test
  void reusesCompilationAcrossSessionsAndInvalidatesChangedContent() throws Exception {
    var source = SourceFile.of(directory.resolve("main.norm"), "Void main() { var value = 1 }");
    try (var compiler = CompilerSession.persistent(directory.resolve("cache"))) {
      assertTrue(compiler.compile(source).isSuccess());
    }
    try (var compiler = CompilerSession.persistent(directory.resolve("cache"))) {
      var result = compiler.compile(source);
      assertTrue(result.isSuccess());
      assertEquals(
          0, result.output().orElseThrow().state().analysisReport().analyzedDeclarations());
      assertTrue(result.output().orElseThrow().state().analysisReport().reusedDeclarations() > 0);
      assertFalse(
          compiler
              .compile(SourceFile.of(source.path(), "Void main() { missingName() }"))
              .isSuccess());
    }
  }
}
