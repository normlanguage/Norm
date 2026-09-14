package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleGraph;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublishedCoreImportTest {
  @TempDir Path directory;

  @Test
  void readsModuleIdentityWithoutDecodingCoreBodies() throws Exception {
    var coordinate = new ModuleCoordinate("deferred", 1);
    var payload =
        new CompiledModule.Payload(
            CompiledModule.ABI,
            dev.w0fv1.norm.core.CoreIdentityVersion.CURRENT,
            coordinate,
            7,
            Map.of(),
            new byte[] {1});
    var module =
        CompiledModule.decode(
            dev.w0fv1.norm.core.store.PortableObjectCodec.encodeDeterministic(payload));
    assertEquals(coordinate, module.coordinate());
    assertEquals(7, module.nextSymbolOrdinal);
    assertNotNull(module.contentId());
    assertThrows(CompilationInfrastructureException.class, module::content);
  }

  @Test
  void importsModulesWithoutNormDeclarations() {
    var empty = new ModuleCoordinate("empty", 1);
    var app = new ModuleCoordinate("application", 1);
    var anchor = SourceFile.of(directory.resolve("publisher/module.norm"), "");
    CompiledModule module;
    try (var compiler = new CompilerSession()) {
      var result =
          compiler.compileModule(
              request(empty, Map.of(empty, anchor), Map.of(empty, Set.of())).asLibrary(), empty);
      assertTrue(result.compilation().isSuccess(), result.compilation().diagnostics().toString());
      module = result.module().orElseThrow();
    }
    var main =
        SourceFile.of(directory.resolve("consumer/main.norm"), "Void main() { printLine(42) }");
    try (var compiler = new CompilerSession()) {
      var result =
          compiler.compile(
              request(app, Map.of(app, main), Map.of(empty, Set.of(), app, Set.of(empty))),
              List.of(module));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      assertEquals(1, result.output().orElseThrow().state().buildReport().convertedDefinitions());
      assertEquals(0, result.output().orElseThrow().state().buildReport().importedDefinitions());
    }
  }

  @Test
  void linksIndependentModulesToCurrentDependencyImplementations() throws Exception {
    var base = new ModuleCoordinate("base", 1);
    var facade = new ModuleCoordinate("facade", 1);
    var app = new ModuleCoordinate("application", 1);
    var baseSource =
        SourceFile.of(
            directory.resolve("publisher/base.norm"),
            "package base public Integer value(Integer input = 1) { input }");
    var facadeSource =
        SourceFile.of(
            directory.resolve("publisher/facade.norm"),
            "package facade import base.value public Integer first() { value() } public Integer second() { value() }");
    var baseRequest = request(base, Map.of(base, baseSource), Map.of(base, Set.of())).asLibrary();
    CompiledModule originalBase;
    CompiledModule publishedFacade;
    try (var compiler = new CompilerSession()) {
      originalBase =
          CompiledModule.decode(
              compiler.compileModule(baseRequest, base).module().orElseThrow().encode());
      var facadeRequest =
          request(
                  facade,
                  Map.of(base, baseSource, facade, facadeSource),
                  Map.of(base, Set.of(), facade, Set.of(base)))
              .asLibrary();
      var result = compiler.compileModule(facadeRequest, facade, List.of(originalBase));
      assertTrue(result.compilation().isSuccess(), result.compilation().diagnostics().toString());
      assertEquals(
          2,
          result.compilation().output().orElseThrow().state().buildReport().convertedDefinitions());
      assertEquals(
          2,
          result.compilation().output().orElseThrow().state().buildReport().importedDefinitions());
      publishedFacade = CompiledModule.decode(result.module().orElseThrow().encode());
    }
    var currentBase =
        SourceFile.of(
            directory.resolve("consumer/base.norm"),
            baseSource.text().replace("input = 1", "input = 9"));
    var currentFacade =
        SourceFile.of(directory.resolve("consumer/facade.norm"), facadeSource.text());
    var main =
        SourceFile.of(
            directory.resolve("consumer/main.norm"),
            "package app import facade.first import facade.second Void main() { printLine(first() + second()) }");
    CompiledModule updatedBase;
    try (var compiler = new CompilerSession()) {
      updatedBase =
          CompiledModule.decode(
              compiler
                  .compileModule(
                      request(base, Map.of(base, currentBase), Map.of(base, Set.of())).asLibrary(),
                      base)
                  .module()
                  .orElseThrow()
                  .encode());
    }
    var request =
        request(
            app,
            Map.of(base, currentBase, facade, currentFacade, app, main),
            Map.of(base, Set.of(), facade, Set.of(base), app, Set.of(facade)));
    var cache = directory.resolve("cache");
    try (var compiler = CompilerSession.persistent(cache);
        var fresh = new CompilerSession()) {
      var result = compiler.compile(request, List.of(publishedFacade, updatedBase));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var output = result.output().orElseThrow();
      assertEquals(1, output.state().buildReport().convertedDefinitions());
      assertEquals(4, output.state().buildReport().importedDefinitions());
      assertEquals(1, output.state().analysisReport().analyzedDeclarations());
      assertEquals(
          ArtifactId.forArtifact(fresh.compile(request).output().orElseThrow().artifact(), "test"),
          ArtifactId.forArtifact(output.artifact(), "test"));
      var printed = new java.io.StringWriter();
      new dev.w0fv1.norm.runtime.NormRuntime()
          .run(output.artifact(), new java.io.PrintWriter(printed));
      assertEquals("18" + System.lineSeparator(), printed.toString());
    }
    var edited =
        SourceFile.of(
            main.path(), main.text().replace("first() + second()", "first() + second() + 1"));
    try (var compiler = CompilerSession.persistent(cache)) {
      var result =
          compiler.compile(
              request(
                  app,
                  Map.of(base, currentBase, facade, currentFacade, app, edited),
                  request.scope().modules().dependencies()),
              List.of(updatedBase, publishedFacade));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      assertEquals(1, result.output().orElseThrow().state().buildReport().convertedDefinitions());
      assertEquals(0, result.output().orElseThrow().state().buildReport().importedDefinitions());
      assertEquals(4, result.output().orElseThrow().state().buildReport().reusedDefinitions());
      var printed = new java.io.StringWriter();
      new dev.w0fv1.norm.runtime.NormRuntime()
          .run(result.output().orElseThrow().artifact(), new java.io.PrintWriter(printed));
      assertEquals("19" + System.lineSeparator(), printed.toString());
    }
    try (var compiler = new CompilerSession()) {
      assertThrows(
          IllegalArgumentException.class,
          () -> compiler.compile(request, List.of(originalBase, publishedFacade)));
      var changedContract =
          SourceFile.of(
              currentBase.path(),
              "package base public String value(String input = \"changed\") { input }");
      var incompatible =
          request(
              app,
              Map.of(base, changedContract, facade, currentFacade, app, main),
              request.scope().modules().dependencies());
      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> compiler.compile(incompatible, List.of(publishedFacade)));
      assertTrue(failure.getMessage().contains("contract"));
    }
  }

  private static CompilationRequest request(
      ModuleCoordinate entry,
      Map<ModuleCoordinate, SourceFile> documents,
      Map<ModuleCoordinate, Set<ModuleCoordinate>> reads) {
    var source = documents.get(entry);
    var coordinates =
        new java.util.LinkedHashMap<dev.w0fv1.norm.source.DocumentId, ModuleSourceCoordinate>();
    documents.forEach(
        (module, document) ->
            coordinates.put(
                document.id(),
                new ModuleSourceCoordinate(module, document.path().getFileName().toString())));
    return new CompilationRequest(
        new CompilationUnitId(source.id().uri()),
        new CompilationScope(coordinates, new ModuleGraph(reads)),
        source.id(),
        List.copyOf(documents.values()),
        Set.copyOf(coordinates.keySet()));
  }

  @Test
  void importsPublishedBodiesWithDefaultsGenericsAndCapturedLocals() throws Exception {
    var library = new ModuleCoordinate("library", 1);
    var application = new ModuleCoordinate("application", 1);
    String text =
        """
        package library
        private T identity<T>(T value) { value }
        public Integer value(Integer input = 41) { identity(input) }
        public Function<Integer()> deferred(Integer input) { Integer captured = input; () { captured } }
        """;
    var publishedSource = SourceFile.of(directory.resolve("publisher/library.norm"), text);
    var publishing =
        new CompilationRequest(
                new CompilationUnitId(publishedSource.id().uri()),
                CompilationScope.module(library, Map.of(publishedSource.id(), "library.norm")),
                publishedSource.id(),
                List.of(publishedSource),
                Set.of(publishedSource.id()))
            .asLibrary();
    CompiledModule published;
    int definitions;
    try (var compiler = new CompilerSession()) {
      var result = compiler.compileModule(publishing, library);
      assertTrue(result.compilation().isSuccess(), result.compilation().diagnostics().toString());
      definitions = result.compilation().output().orElseThrow().state().buildReport().definitions();
      published = CompiledModule.decode(result.module().orElseThrow().encode());
    }
    var consumedSource = SourceFile.of(directory.resolve("consumer/library.norm"), text);
    var main =
        SourceFile.of(
            directory.resolve("consumer/main.norm"),
            """
        package app
        import library.value
        import library.deferred
        Void main() { printLine(value()); Function<Integer()> callback = deferred(7); printLine(callback()) }
        """);
    var scope =
        new CompilationScope(
            Map.of(
                consumedSource.id(), new ModuleSourceCoordinate(library, "library.norm"),
                main.id(), new ModuleSourceCoordinate(application, "main.norm")),
            new ModuleGraph(Map.of(library, Set.of(), application, Set.of(library))));
    var request =
        new CompilationRequest(
            new CompilationUnitId(main.id().uri()),
            scope,
            main.id(),
            List.of(main, consumedSource),
            Set.of(consumedSource.id()));
    try (var compiler = new CompilerSession();
        var fresh = new CompilerSession()) {
      assertTrue(compiler.compile(request).isSuccess());
      var result = compiler.compile(request, List.of(published));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var output = result.output().orElseThrow();
      assertEquals(1, output.state().analysisReport().analyzedDeclarations());
      assertEquals(1, output.state().buildReport().convertedDefinitions());
      assertEquals(definitions, output.state().buildReport().importedDefinitions());
      var expected = fresh.compile(request);
      assertTrue(expected.isSuccess(), expected.diagnostics().toString());
      assertEquals(
          ArtifactId.forArtifact(expected.output().orElseThrow().artifact(), "test"),
          ArtifactId.forArtifact(output.artifact(), "test"));
      assertTrue(
          output.artifact().authoring().occurrences().stream()
              .allMatch(
                  occurrence ->
                      !occurrence.origin().rootSpan().source().id().equals(publishedSource.id())));
      var printed = new java.io.StringWriter();
      new dev.w0fv1.norm.runtime.NormRuntime()
          .run(output.artifact(), new java.io.PrintWriter(printed));
      assertEquals(
          "41" + System.lineSeparator() + "7" + System.lineSeparator(), printed.toString());
    }
  }
}
