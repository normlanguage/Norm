package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreCodeId;
import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LibraryCompilationTest {
  @TempDir Path directory;

  @Test
  void compilesAndRoundTripsAGenericLibraryWithoutAnApplicationEntry() throws Exception {
    var source =
        SourceFile.of(
            directory.resolve("library.norm"), "public T identity<T>(T value) { return value }");
    try (var compiler = new CompilerSession()) {
      var result = compiler.compile(CompilationRequest.single(source).asLibrary());
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var artifact = result.output().orElseThrow().artifact();
      assertTrue(artifact.authoring().entryPoint().isEmpty());
      assertFalse(artifact.program().definitions().isEmpty());
      var restored =
          PortableObjectCodec.decode(PortableObjectCodec.encode(artifact), CoreArtifact.class);
      assertEquals(
          ArtifactId.forArtifact(artifact, "test"), ArtifactId.forArtifact(restored, "test"));
      assertEquals(CoreCodeId.forArtifact(artifact), CoreCodeId.forArtifact(restored));
      assertThrows(IllegalStateException.class, artifact::entryPoint);
    }
  }

  @Test
  void treatsMainAsAnOrdinaryLibraryFunction() {
    var source =
        SourceFile.of(
            directory.resolve("library.norm"),
            "public Integer main(Integer value) { return value }");
    try (var compiler = new CompilerSession()) {
      var result = compiler.compile(CompilationRequest.single(source).asLibrary());
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      assertTrue(result.output().orElseThrow().artifact().authoring().entryPoint().isEmpty());
    }
  }

  @Test
  void permitsEmptyLibrariesAndChecksLibraryBodies() {
    try (var compiler = new CompilerSession()) {
      var empty = CompilationRequest.single(SourceFile.of(directory.resolve("empty.norm"), ""));
      var result = compiler.compile(empty.asLibrary());
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      assertTrue(result.output().orElseThrow().artifact().program().definitions().isEmpty());
      var broken =
          SourceFile.of(
              directory.resolve("broken.norm"), "public Integer value() { return missing() }");
      var failed = compiler.compile(CompilationRequest.single(broken).asLibrary());
      assertFalse(failed.isSuccess());
      assertTrue(
          failed.diagnostics().stream().anyMatch(value -> value.message().contains("missing")));
    }
  }

  @Test
  void doesNotReuseALibraryResultAsAnApplicationInMemoryOrOnDisk() throws Exception {
    var request =
        CompilationRequest.single(
            SourceFile.of(directory.resolve("library.norm"), "Integer value() { return 42 }"));
    var cache = directory.resolve("cache");
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(request.asLibrary()).isSuccess());
      assertFalse(compiler.compile(request).isSuccess());
    }
    try (var compiler = CompilerSession.persistent(cache)) {
      assertTrue(compiler.compile(request.asLibrary()).isSuccess());
      assertFalse(compiler.compile(request).isSuccess());
    }
  }
}
