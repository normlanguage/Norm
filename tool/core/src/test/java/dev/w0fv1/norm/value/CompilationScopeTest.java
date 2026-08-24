package dev.w0fv1.norm.value;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class CompilationScopeTest {
  @Test
  void rejectsDuplicateLogicalSourcePaths() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CompilationScope(
                new ModuleCoordinate("sample", 1),
                Map.of(
                    DocumentId.of("memory:/first.norm"), "sample/Value.norm",
                    DocumentId.of("memory:/second.norm"), "sample/Value.norm")));
  }

  @Test
  void rejectsNonCanonicalLogicalSourcePaths() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CompilationScope(
                new ModuleCoordinate("sample", 1),
                Map.of(DocumentId.of("memory:/value.norm"), "sample//Value.norm")));
  }
}
