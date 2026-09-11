package dev.w0fv1.norm.project;

import static dev.w0fv1.norm.project.ProjectPaths.normalize;

import dev.w0fv1.norm.frontend.ModuleLoader;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.ModuleDescriptor;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

record ResolvedProjectModule(
    Path root,
    SourceFile moduleSource,
    ModuleDescriptor descriptor,
    Map<String, SourceFile> sources,
    Set<DocumentId> exportedSources,
    Set<DocumentId> bindingSources,
    Optional<ResolvedJarBinding> binding,
    Map<String, ModuleResource> resources,
    Optional<FileSnapshot> archive,
    Set<DocumentId> testSources) {
  ResolvedProjectModule(
      Path root,
      SourceFile moduleSource,
      ModuleDescriptor descriptor,
      Map<String, SourceFile> sources,
      Set<DocumentId> exportedSources,
      Set<DocumentId> bindingSources,
      Optional<ResolvedJarBinding> binding,
      Map<String, ModuleResource> resources,
      Optional<FileSnapshot> archive) {
    this(
        root,
        moduleSource,
        descriptor,
        sources,
        exportedSources,
        bindingSources,
        binding,
        resources,
        archive,
        Set.of());
  }

  ResolvedProjectModule {
    testSources = Set.copyOf(testSources);
    root = normalize(root);
    Objects.requireNonNull(moduleSource, "moduleSource");
    Objects.requireNonNull(descriptor, "descriptor");
    sources = Map.copyOf(sources);
    exportedSources = Set.copyOf(exportedSources);
    bindingSources = Set.copyOf(bindingSources);
    Objects.requireNonNull(binding, "binding");
    resources = Map.copyOf(resources);
  }

  static Set<DocumentId> exportedSources(
      ModuleLoader.LoadedModule loaded, Set<DocumentId> bindingSources) {
    Set<DocumentId> result = new LinkedHashSet<>(loaded.exportedSources());
    result.addAll(bindingSources);
    return Set.copyOf(result);
  }
}
