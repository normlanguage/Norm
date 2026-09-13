package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PersistentCompilationTest {
  @TempDir Path directory;

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
      assertTrue(result.output().orElseThrow().state().analysisReport().analyzedDeclarations() > 0);
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
