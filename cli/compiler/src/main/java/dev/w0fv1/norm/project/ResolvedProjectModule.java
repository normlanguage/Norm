package dev.w0fv1.norm.project;

import static dev.w0fv1.norm.project.ProjectPaths.normalize;

import dev.w0fv1.norm.execution.JarBindingClassReference;
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
    Set<DocumentId> testSources,
    Map<String, JarBindingClassReference.Nominal> archivedJavaExports,
    Optional<dev.w0fv1.norm.frontend.CompiledModule> compiled) {
  ResolvedProjectModule(
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
        testSources,
        Map.of(),
        Optional.empty());
  }

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
    Objects.requireNonNull(compiled, "compiled");
    archivedJavaExports = Map.copyOf(archivedJavaExports);
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
