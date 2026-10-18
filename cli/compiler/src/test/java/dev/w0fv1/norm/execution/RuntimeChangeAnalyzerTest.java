package dev.w0fv1.norm.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class RuntimeChangeAnalyzerTest {
  @Test
  void reportsModifiedFunctionsAndTypesWithBeforeAndAfterShapes() {
    try (var compiler = new CompilerSession()) {
      CoreArtifact before =
          compile(
              compiler, "class Counter { Integer value }\nInteger answer() { 1 }\nVoid main() {}");
      CoreArtifact after =
          compile(compiler, "class Counter { Long value }\nInteger answer() { 2 }\nVoid main() {}");

      var changes = new RuntimeChangeAnalyzer().compare(before, after).declarations();

      assertEquals(3, changes.size());
      assertTrue(changes.stream().allMatch(change -> change.kind() == RuntimeChangeKind.MODIFIED));
      assertTrue(changes.stream().anyMatch(change -> change.identity().contains("Counter")));
      assertTrue(changes.stream().anyMatch(change -> change.identity().contains("answer")));
      assertTrue(
          changes.stream()
              .allMatch(change -> change.before().isPresent() && change.after().isPresent()));
      var fieldChange =
          changes.stream()
              .filter(change -> change.identity().contains("Counter"))
              .findFirst()
              .orElseThrow();
      assertTrue(fieldChange.before().orElseThrow().sourceContent().contains("Integer value"));
      assertTrue(fieldChange.after().orElseThrow().sourceContent().contains("Long value"));
    }
  }

  private static CoreArtifact compile(CompilerSession compiler, String source) {
    var result = compiler.compile(SourceFile.of(Path.of("runtime-change.norm"), source));
    assertTrue(result.isSuccess(), result.diagnostics().toString());
    return result.output().orElseThrow().artifact();
  }
}
