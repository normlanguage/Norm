package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreDefinitionRole;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DefaultArgumentReuseTest {
  @Test
  void sharesDefaultClosuresAndReusesThemAfterEditingACaller(@TempDir Path directory)
      throws Exception {
    String original =
        """
        Integer invoke(Function<Integer()> work = () { 41 }) { work() }
        Integer first() { invoke() }
        Integer second() { invoke() }
        Void main() { printLine(first()); printLine(second()) }
        """;
    Path file = directory.resolve("main.norm");
    Path cache = directory.resolve("cache");
    CoreArtifact first;
    try (var compiler = CompilerSession.persistent(cache)) {
      var result = compiler.compile(SourceFile.of(file, original));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      first = result.output().orElseThrow().artifact();
    }
    assertEquals(
        1,
        first.authoring().occurrences().stream()
            .filter(value -> value.role() == CoreDefinitionRole.DEFAULT_ARGUMENT)
            .count());
    assertEquals(
        1,
        first.authoring().occurrences().stream()
            .filter(value -> value.role() == CoreDefinitionRole.LAMBDA)
            .count());
    String edited = original.replace("first() { invoke() }", "first() { invoke() + 1 }");
    try (var compiler = CompilerSession.persistent(cache);
        var fresh = new CompilerSession()) {
      var source = SourceFile.of(file, edited);
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var output = result.output().orElseThrow();
      assertEquals(1, output.state().buildReport().convertedDefinitions());
      assertEquals(1, output.state().buildReport().relinkedDefinitions());
      var expected = fresh.compile(source).output().orElseThrow().artifact();
      assertEquals(
          ArtifactId.forArtifact(expected, "test"),
          ArtifactId.forArtifact(output.artifact(), "test"));
      assertEquals(
          first.authoring().occurrences().stream()
              .filter(value -> value.role() == CoreDefinitionRole.DEFAULT_ARGUMENT)
              .map(dev.w0fv1.norm.core.CoreDefinitionOccurrence::id)
              .toList(),
          output.artifact().authoring().occurrences().stream()
              .filter(value -> value.role() == CoreDefinitionRole.DEFAULT_ARGUMENT)
              .map(dev.w0fv1.norm.core.CoreDefinitionOccurrence::id)
              .toList());
    }
    try (var compiler = CompilerSession.persistent(cache);
        var fresh = new CompilerSession()) {
      var source = SourceFile.of(file, edited.replace("41", "42"));
      var result = compiler.compile(source);
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var expected = fresh.compile(source).output().orElseThrow().artifact();
      assertEquals(
          ArtifactId.forArtifact(expected, "test"),
          ArtifactId.forArtifact(result.output().orElseThrow().artifact(), "test"));
    }
  }
}
