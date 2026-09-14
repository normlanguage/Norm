package dev.w0fv1.norm.project;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleGraph;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record ProjectCompilationSources(
    List<SourceFile> sources,
    CompilationScope scope,
    Set<DocumentId> exports,
    Set<DocumentId> bindings) {
  ProjectCompilationSources {
    sources = List.copyOf(sources);
    exports = Set.copyOf(exports);
    bindings = Set.copyOf(bindings);
  }

  static ProjectCompilationSources from(List<ResolvedProjectModule> modules) {
    var sources = new ArrayList<SourceFile>();
    var coordinates = new LinkedHashMap<DocumentId, ModuleSourceCoordinate>();
    var exports = new LinkedHashSet<DocumentId>();
    var bindings = new LinkedHashSet<DocumentId>();
    var tests = new LinkedHashSet<DocumentId>();
    var descriptors = new LinkedHashMap<ModuleCoordinate, dev.w0fv1.norm.value.ModuleDescriptor>();
    modules.forEach(
        module -> descriptors.put(module.descriptor().coordinate(), module.descriptor()));
    var dependencies = new LinkedHashMap<ModuleCoordinate, Set<ModuleCoordinate>>();
    for (var module : modules) {
      var descriptor = module.descriptor();
      var readable = new LinkedHashSet<ModuleCoordinate>();
      var pending = new ArrayDeque<ModuleCoordinate>();
      descriptor.dependencies().forEach(requirement -> pending.add(requirement.coordinate()));
      while (!pending.isEmpty()) {
        var coordinate = pending.removeFirst();
        if (!readable.add(coordinate)) continue;
        var dependency = descriptors.get(coordinate);
        if (dependency == null)
          throw new IllegalArgumentException("module dependency is absent: " + coordinate);
        dependency.dependencies().stream()
            .filter(dev.w0fv1.norm.value.ModuleRequirement::exported)
            .forEach(requirement -> pending.add(requirement.coordinate()));
      }
      dependencies.put(descriptor.coordinate(), readable);
      module
          .sources()
          .forEach(
              (path, source) -> {
                sources.add(source);
                coordinates.put(
                    source.id(), new ModuleSourceCoordinate(descriptor.coordinate(), path));
              });
      exports.addAll(module.exportedSources());
      bindings.addAll(module.bindingSources());
      tests.addAll(module.testSources());
    }
    sources.sort(java.util.Comparator.comparing(source -> source.id().uri().toString()));
    return new ProjectCompilationSources(
        sources,
        new CompilationScope(coordinates, new ModuleGraph(dependencies), tests),
        exports,
        bindings);
  }

  CompilationRequest library(ResolvedProjectModule module) {
    var documents = sources;
    var compilationScope = scope;
    if (documents.isEmpty()) {
      var anchor = SourceFile.of(module.moduleSource().id(), "");
      documents = List.of(anchor);
      compilationScope =
          new CompilationScope(
              Map.of(
                  anchor.id(),
                  new ModuleSourceCoordinate(module.descriptor().coordinate(), "module.norm")),
              scope.modules());
    }
    return new CompilationRequest(
        new CompilationUnitId(module.moduleSource().id().uri()),
        compilationScope,
        documents.getFirst().id(),
        documents,
        exports,
        bindings,
        CompilationRequest.Kind.LIBRARY);
  }
}
