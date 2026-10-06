package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CompilationPreludeTest {
  @TempDir Path directory;

  @Test
  void usesGeneratedScalarParentsForNominalAssignmentsAndGenericBounds() {
    var owner =
        SourceFile.of(
            DocumentId.of("stdlib:/java/base/lang/Readable.norm"),
            "package java.base.lang\npublic interface Readable<T> { Integer size() { return 3 } }\n");
    var scope =
        CompilationScope.module(
            new ModuleCoordinate("java.base", 1), Map.of(owner.id(), "lang/Readable.norm"));
    var target =
        dev.w0fv1.norm.semantic.SemanticType.declared(
            "java.base.lang.Readable",
            "Readable",
            List.of(dev.w0fv1.norm.semantic.SemanticType.STRING),
            dev.w0fv1.norm.semantic.ValueCategory.POLYMORPHIC);
    var relation =
        new dev.w0fv1.norm.semantic.BuiltinTypeConformance(
            dev.w0fv1.norm.semantic.SemanticType.STRING, target);
    var prelude =
        new CompilationPrelude(
            List.of(owner),
            Set.of(owner.id()),
            scope,
            Set.of(owner.id()),
            Map.of(owner.id(), List.of(relation)));
    var entry =
        SourceFile.of(
            directory.resolve("Main.norm"),
            """
        import java.base.lang.Readable
        Integer measure<T extends Readable<String>>(T input) { return input.size() }
        Void main() {
          Readable<String> text = "hello"
          printLine(measure("world"))
          printLine(text.size())
        }
        """);
    try (var compiler = new CompilerSession(LanguageProfile.withPrelude(prelude))) {
      var result = compiler.snapshot(CompilationRequest.single(entry)).analysis();
      assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
      var overlay =
          SourceFile.of(
              owner.id(),
              owner.text() + "\npublic Readable<String> inspect() { return \"hello\" }\n");
      var overlayResult = compiler.preludeSnapshot(overlay).analysis();
      assertFalse(overlayResult.hasErrors(), () -> overlayResult.diagnostics().toString());
    }
  }

  @Test
  void preservesScalarConformanceOwnershipAndCompilationIdentity() throws Exception {
    var owner =
        SourceFile.of(
            DocumentId.of("stdlib:/java/base/lang/Readable.norm"),
            "package java.base.lang\npublic interface Readable {}\n");
    var coordinate = new ModuleCoordinate("java.base", 1);
    var scope = CompilationScope.module(coordinate, Map.of(owner.id(), "lang/Readable.norm"));
    var relation =
        new dev.w0fv1.norm.semantic.BuiltinTypeConformance(
            dev.w0fv1.norm.semantic.SemanticType.STRING,
            dev.w0fv1.norm.semantic.SemanticType.declared(
                "java.base.lang.Readable",
                "Readable",
                List.of(),
                dev.w0fv1.norm.semantic.ValueCategory.POLYMORPHIC));
    var ordinary =
        new CompilationPrelude(List.of(owner), Set.of(owner.id()), scope, Set.of(owner.id()));
    var generated =
        new CompilationPrelude(
            List.of(owner),
            Set.of(owner.id()),
            scope,
            Set.of(owner.id()),
            Map.of(owner.id(), List.of(relation)));
    assertEquals(List.of(relation), generated.builtinTypeConformances().get(owner.id()));
    assertEquals(
        generated.builtinTypeConformances(),
        generated.merge(CompilationPrelude.empty()).builtinTypeConformances());
    assertTrue(generated.excludingModules(Set.of(coordinate)).builtinTypeConformances().isEmpty());
    var request =
        CompilationRequest.single(
            SourceFile.of(directory.resolve("Main.norm"), "Void main() {}\n"));
    assertNotEquals(
        new CompilationResultCache(
                directory.resolve("plain"), LanguageProfile.withPrelude(ordinary))
            .key(request),
        new CompilationResultCache(
                directory.resolve("generated"), LanguageProfile.withPrelude(generated))
            .key(request));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CompilationPrelude(
                List.of(owner),
                Set.of(owner.id()),
                scope,
                Set.of(),
                Map.of(owner.id(), List.of(relation))));
  }

  @Test
  void preservesBindingOriginsThroughMergeRequestsAndModuleReplacement() {
    var javaBase = new ModuleCoordinate("java.base", 1);
    var standard = new ModuleCoordinate("std", 1);
    var binding =
        SourceFile.of(
            DocumentId.of("stdlib:/java/base/util/Map.norm"),
            "package java.base.util\npublic class Map {}\n");
    var facade =
        SourceFile.of(
            DocumentId.of("stdlib:/std/collections/maps.norm"),
            "package std.collections\npublic Void maps() {}\n");
    var javaPrelude =
        new CompilationPrelude(
            List.of(binding),
            Set.of(binding.id()),
            CompilationScope.module(javaBase, Map.of(binding.id(), "java/base/util/Map.norm")),
            Set.of(binding.id()));
    var stdPrelude =
        new CompilationPrelude(
            List.of(facade),
            Set.of(facade.id()),
            CompilationScope.module(standard, Map.of(facade.id(), "std/collections/maps.norm")));
    var merged = javaPrelude.merge(stdPrelude);

    assertEquals(Set.of(binding.id()), merged.bindingSources());
    assertEquals(Set.of(binding.id()), merged.request(facade.id()).bindingSources());
    assertEquals(Set.of(binding.id()), merged.excludingModules(Set.of(standard)).bindingSources());
    assertTrue(merged.excludingModules(Set.of(javaBase)).bindingSources().isEmpty());
  }

  @Test
  void rejectsBindingOriginsOutsidePreludeSources() {
    var source =
        SourceFile.of(DocumentId.of("stdlib:/java/base/util/Map.norm"), "public class Map {}\n");
    var scope =
        CompilationScope.module(
            new ModuleCoordinate("java.base", 1), Map.of(source.id(), "Map.norm"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CompilationPrelude(
                List.of(source), Set.of(), scope, Set.of(DocumentId.of("stdlib:/foreign.norm"))));
  }

  @Test
  void includesBindingOriginsInPersistentCompilationIdentity() throws Exception {
    var source =
        SourceFile.of(DocumentId.of("stdlib:/java/base/util/Map.norm"), "public class Map {}\n");
    var scope =
        CompilationScope.module(
            new ModuleCoordinate("java.base", 1), Map.of(source.id(), "Map.norm"));
    var ordinary = new CompilationPrelude(List.of(source), Set.of(source.id()), scope);
    var generated =
        new CompilationPrelude(List.of(source), Set.of(source.id()), scope, Set.of(source.id()));
    var entry = SourceFile.of(directory.resolve("Main.norm"), "Void main() {}\n");
    var request = CompilationRequest.single(entry);
    var ordinaryCache =
        new CompilationResultCache(
            directory.resolve("ordinary"), LanguageProfile.withPrelude(ordinary));
    var generatedCache =
        new CompilationResultCache(
            directory.resolve("generated"), LanguageProfile.withPrelude(generated));

    assertNotEquals(ordinaryCache.key(request), generatedCache.key(request));
  }

  @Test
  void grantsBindingCallsOnlyToGeneratedPreludeDocuments() {
    var binding =
        SourceFile.of(
            DocumentId.of("generated:/Probe.norm"),
            "package java.base\npublic Integer probe() { return __jarInvoke0<Integer>(call: \"java.base.probe\") }\n");
    var scope =
        CompilationScope.module(
            new ModuleCoordinate("java.base", 1), Map.of(binding.id(), "Probe.norm"));
    var ordinary = new CompilationPrelude(List.of(binding), Set.of(binding.id()), scope);
    var generated =
        new CompilationPrelude(List.of(binding), Set.of(binding.id()), scope, Set.of(binding.id()));
    var entry =
        SourceFile.of(
            directory.resolve("Main.norm"),
            "import java.base.probe\nVoid main() { printLine(probe()) }\n");

    try (var compiler = new CompilerSession(LanguageProfile.withPrelude(ordinary))) {
      assertTrue(compiler.snapshot(CompilationRequest.single(entry)).analysis().hasErrors());
    }
    try (var compiler = new CompilerSession(LanguageProfile.withPrelude(generated))) {
      var result = compiler.snapshot(CompilationRequest.single(entry)).analysis();
      assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
    }
  }
}
