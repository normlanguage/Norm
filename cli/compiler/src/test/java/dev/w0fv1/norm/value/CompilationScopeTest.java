package dev.w0fv1.norm.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CompilationScopeTest {
  @Test
  void rejectsDuplicateLogicalSourcePaths() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CompilationScope.module(
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
            CompilationScope.module(
                new ModuleCoordinate("sample", 1),
                Map.of(DocumentId.of("memory:/value.norm"), "sample//Value.norm")));
  }

  @Test
  void assignsEachSourceToItsOwningModule() {
    DocumentId first = DocumentId.of("memory:/first/Main.norm");
    DocumentId second = DocumentId.of("memory:/second/Main.norm");
    ModuleCoordinate firstModule = new ModuleCoordinate("first", 1);
    ModuleCoordinate secondModule = new ModuleCoordinate("second", 2);
    CompilationScope scope =
        new CompilationScope(
            Map.of(
                first, new ModuleSourceCoordinate(firstModule, "Main.norm"),
                second, new ModuleSourceCoordinate(secondModule, "Main.norm")));

    assertEquals(firstModule, scope.coordinate(first).module());
    assertEquals(secondModule, scope.coordinate(second).module());
  }

  @Test
  void grantsOnlyDirectModuleDependencies() {
    DocumentId application = DocumentId.of("memory:/application/Main.norm");
    DocumentId middle = DocumentId.of("memory:/middle/Value.norm");
    DocumentId base = DocumentId.of("memory:/base/Value.norm");
    ModuleCoordinate applicationModule = new ModuleCoordinate("application", 1);
    ModuleCoordinate middleModule = new ModuleCoordinate("middle", 1);
    ModuleCoordinate baseModule = new ModuleCoordinate("base", 1);
    CompilationScope scope =
        new CompilationScope(
            Map.of(
                application, new ModuleSourceCoordinate(applicationModule, "Main.norm"),
                middle, new ModuleSourceCoordinate(middleModule, "Value.norm"),
                base, new ModuleSourceCoordinate(baseModule, "Value.norm")),
            new ModuleGraph(
                Map.of(
                    applicationModule, Set.of(middleModule),
                    middleModule, Set.of(baseModule),
                    baseModule, Set.of())));

    assertTrue(scope.canRead(application, middle));
    assertTrue(scope.canRead(middle, base));
    assertFalse(scope.canRead(application, base));
    assertFalse(scope.canRead(base, application));
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            scope
                .modules()
                .dependencies()
                .get(applicationModule)
                .add(new ModuleCoordinate("other", 1)));
  }
}
