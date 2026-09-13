package dev.w0fv1.norm.project;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.frontend.ModuleLoader;
import dev.w0fv1.norm.jvm.JarApiScanner;
import dev.w0fv1.norm.jvm.JarBindingSourceGenerator;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ProjectJarBindingLinker {
  private ProjectJarBindingLinker() {}

  static Map<String, JarBindingClassReference.Nominal> exports(ResolvedJarBinding binding) {
    Map<String, JarBindingClassReference.Nominal> result = new LinkedHashMap<>();
    binding
        .generated()
        .classDescriptors()
        .forEach(
            (reference, descriptor) -> {
              String path =
                  (reference.packageName() + "." + reference.name())
                      .substring(reference.module().name().length() + 1);
              if (binding.generated().exports().contains(path) && descriptor.startsWith("L")) {
                result.put(
                    descriptor.substring(1, descriptor.length() - 1).replace('/', '.'), reference);
              }
            });
    return Map.copyOf(result);
  }

  static ResolvedProjectModule link(
      ResolvedProjectModule module, Map<ModuleCoordinate, ResolvedProjectModule> dependencies)
      throws IOException {
    if (module.binding().isEmpty()) return module;
    Map<String, JarBindingClassReference.Nominal> imports = new LinkedHashMap<>();
    Set<ModuleCoordinate> visited = new LinkedHashSet<>();
    for (var dependency : module.descriptor().dependencies()) {
      collect(dependency.coordinate(), dependencies, visited, imports);
    }
    var previous = module.binding().orElseThrow();
    var descriptor = module.descriptor();
    var api = descriptor.binding().orElseThrow().api();
    var generated = previous.generated();
    if (!imports.isEmpty()) {
      var surface =
          module.archive().isPresent()
              ? previous.api()
              : new JarApiScanner()
                  .scanSurface(
                      previous.graph(),
                      api.stream().map(dev.w0fv1.norm.value.JarBindingType::name).toList());
      generated =
          new JarBindingSourceGenerator()
              .generateSurface(
                  descriptor.coordinate(),
                  descriptor.exports().subList(0, api.size()),
                  api,
                  previous.graph().contentId(),
                  surface,
                  imports);
    }
    var binding = new ResolvedJarBinding(previous.graph(), previous.api(), generated);
    if (!module.archivedJavaExports().isEmpty()
        && !module.archivedJavaExports().equals(exports(binding))) {
      throw new IOException("Norm module public Java types do not match its pinned JAR binding");
    }
    Map<String, SourceFile> sources = new LinkedHashMap<>(module.sources());
    Set<DocumentId> bindingSources = new LinkedHashSet<>();
    if (module.archive().isEmpty()) {
      sources.entrySet().removeIf(entry -> module.bindingSources().contains(entry.getValue().id()));
    }
    for (var source : generated.sources()) {
      SourceFile file;
      if (module.archive().isPresent()) {
        file = sources.get(source.relativePath());
        if (file == null || !file.text().equals(source.text())) {
          throw new IOException(
              "Norm module generated sources do not match its pinned JAR binding");
        }
      } else {
        file = SourceFile.of(module.root().resolve(source.relativePath()), source.text());
        if (sources.putIfAbsent(source.relativePath(), file) != null) {
          throw new IOException("JAR binding source conflicts with " + source.relativePath());
        }
      }
      bindingSources.add(file.id());
    }
    var loaded = new ModuleLoader().load(new ModuleSourceSnapshot(sources), descriptor);
    return new ResolvedProjectModule(
        module.root(),
        module.moduleSource(),
        descriptor,
        loaded.sources(),
        ResolvedProjectModule.exportedSources(loaded, bindingSources),
        bindingSources,
        Optional.of(binding),
        module.resources(),
        module.archive(),
        module.testSources(),
        module.archivedJavaExports());
  }

  private static void collect(
      ModuleCoordinate coordinate,
      Map<ModuleCoordinate, ResolvedProjectModule> dependencies,
      Set<ModuleCoordinate> visited,
      Map<String, JarBindingClassReference.Nominal> imports)
      throws IOException {
    if (!visited.add(coordinate)) return;
    var dependency = dependencies.get(coordinate);
    if (dependency == null)
      throw new IOException("unresolved binding dependency " + coordinate.name());
    var publicTypes =
        dependency
            .binding()
            .map(ProjectJarBindingLinker::exports)
            .orElse(dependency.archivedJavaExports());
    for (var entry : publicTypes.entrySet()) {
      var previous = imports.putIfAbsent(entry.getKey(), entry.getValue());
      if (previous != null && !previous.equals(entry.getValue())) {
        throw new IOException(
            "ambiguous public Java type "
                + entry.getKey()
                + ": "
                + previous
                + " and "
                + entry.getValue());
      }
    }
    for (var next : dependency.descriptor().dependencies()) {
      if (next.exported()) collect(next.coordinate(), dependencies, visited, imports);
    }
  }
}
