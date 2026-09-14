package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PortableDeclarationIdentityTest {
  @TempDir Path directory;

  @Test
  void preservesDeclarationsAndPrivateTypesWhenTheModuleRootMoves() {
    String text =
        """
        package sample
        import sample.identity as selected
        private class Hidden<T> { T value }
        public T identity<T>(T value) { value }
        public Integer answer(Integer value = 42) { Hidden<Integer>(value: value).value }
        public Function<Integer()> deferred(Integer input) { Integer captured = selected(input); () { captured } }
        """;
    var first = SourceFile.of(directory.resolve("publisher/library.norm"), text);
    var second = SourceFile.of(directory.resolve("consumer/library.norm"), text);
    var module = new ModuleCoordinate("sample", 3);
    try (var compiler = new CompilerSession()) {
      var before = compiler.snapshot(request(first, module, "library.norm"));
      var after = compiler.snapshot(request(second, module, "library.norm"));
      assertFalse(before.analysis().hasErrors(), before.diagnostics().toString());
      assertFalse(after.analysis().hasErrors(), after.diagnostics().toString());
      assertEquals(declarations(before), declarations(after));
      var plan =
          IncrementalAnalysisPlan.create(
              before.history(),
              List.of(SourceParser.parse(second)),
              request(second, module, "library.norm").scope(),
              after.declarations());
      assertEquals(0, plan.analyzedDeclarations());
      var expected =
          after.semanticModel().contributions(plan.reusable().keySet().stream().toList());
      assertEquals(expected, plan.reusable());
      assertFalse(declarations(after).isEmpty());
      assertTrue(
          after.semanticModel().symbols().stream()
              .filter(symbol -> symbol.id().value().startsWith("authored/"))
              .flatMap(symbol -> symbol.declaration().stream())
              .allMatch(location -> location.document().equals(second.id())));
    }
  }

  @Test
  void preservesImportAliasesWhenBodySymbolOrdinalsAdvance() {
    String text =
        "package sample import sample.identity as selected public T identity<T>(T value) { value } public Integer changed() { 1 } public Integer stable() { selected(42) }";
    var source = SourceFile.of(directory.resolve("library.norm"), text);
    var module = new ModuleCoordinate("sample", 3);
    try (var compiler = new CompilerSession()) {
      var before = compiler.snapshot(request(source, module, "library.norm"));
      var after =
          compiler.snapshot(
              request(
                  SourceFile.of(source.path(), text.replace("{ 1 }", "{ 2 }")),
                  module,
                  "library.norm"));
      assertFalse(before.analysis().hasErrors(), before.diagnostics().toString());
      assertFalse(after.analysis().hasErrors(), after.diagnostics().toString());
      assertEquals(
          before.semanticModel().symbols().stream()
              .filter(symbol -> symbol.name().equals("selected"))
              .map(Symbol::id)
              .toList(),
          after.semanticModel().symbols().stream()
              .filter(symbol -> symbol.name().equals("selected"))
              .map(Symbol::id)
              .toList());
    }
  }

  @Test
  void distinguishesModuleCoordinatesAndModuleRelativeSourcePaths() {
    var source =
        SourceFile.of(
            directory.resolve("library.norm"),
            "package sample private class Hidden<T> { T value } public T identity<T>(T value) { value }");
    try (var compiler = new CompilerSession()) {
      var original =
          compiler.snapshot(request(source, new ModuleCoordinate("sample", 1), "library.norm"));
      var version =
          compiler.snapshot(request(source, new ModuleCoordinate("sample", 2), "library.norm"));
      var otherModule =
          compiler.snapshot(request(source, new ModuleCoordinate("other", 1), "library.norm"));
      var otherFile =
          compiler.snapshot(
              request(source, new ModuleCoordinate("sample", 1), "nested/library.norm"));
      for (var changed : List.of(version, otherModule, otherFile)) {
        assertFalse(changed.analysis().hasErrors(), changed.diagnostics().toString());
        assertTrue(
            java.util.Collections.disjoint(
                declarations(original).keySet(), declarations(changed).keySet()));
        var beforeType =
            original.semanticModel().symbols().stream()
                .filter(symbol -> symbol.name().equals("Hidden"))
                .findFirst()
                .orElseThrow()
                .type();
        var afterType =
            changed.semanticModel().symbols().stream()
                .filter(symbol -> symbol.name().equals("Hidden"))
                .findFirst()
                .orElseThrow()
                .type();
        assertNotEquals(beforeType, afterType);
      }
    }
  }

  private static CompilationRequest request(
      SourceFile source, ModuleCoordinate module, String relativePath) {
    return new CompilationRequest(
            new CompilationUnitId(source.id().uri()),
            CompilationScope.module(module, Map.of(source.id(), relativePath)),
            source.id(),
            List.of(source),
            Set.of(source.id()))
        .asLibrary();
  }

  private static Map<SymbolId, Symbol> declarations(CompilationSnapshot snapshot) {
    return snapshot.semanticModel().symbols().stream()
        .filter(
            symbol ->
                symbol.id().value().startsWith("authored/")
                    || symbol.id().value().startsWith("source/"))
        .collect(
            Collectors.toMap(
                Symbol::id,
                symbol ->
                    new Symbol(
                        symbol.id(),
                        symbol.name(),
                        symbol.kind(),
                        symbol.type(),
                        Optional.empty(),
                        symbol.owner(),
                        symbol.typeParameters(),
                        symbol.parameters(),
                        symbol.documentation(),
                        symbol.accessor())));
  }
}
