package dev.w0fv1.norm.project;

import static dev.w0fv1.norm.project.ProjectPaths.normalize;
import static dev.w0fv1.norm.project.ProjectPaths.relativePath;
import static dev.w0fv1.norm.project.ProjectPaths.sourceRoot;

import dev.w0fv1.norm.frontend.ModuleLoader;
import dev.w0fv1.norm.jvm.GeneratedBindingSource;
import dev.w0fv1.norm.jvm.GeneratedJarBinding;
import dev.w0fv1.norm.jvm.JarResolver;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.packages.NormPackageResolver;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.JdkModuleTarget;
import dev.w0fv1.norm.value.LocalJarTarget;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDeclaration;
import dev.w0fv1.norm.value.ModuleDependency;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleRequirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class ProjectModuleSources {
  private final ProjectLoadContext modules;
  private final NormPackageResolver packages;
  private final JarResolver jars;
  private final java.util.function.Consumer<String> progress;
  private final ProjectInputTracker inputs;
  private final Map<ModuleCoordinate, ProvidedModule> providedModules;

  ProjectModuleSources(
      ProjectLoadContext modules,
      NormPackageResolver packages,
      JarResolver jars,
      java.util.function.Consumer<String> progress,
      ProjectInputTracker inputs,
      List<ProvidedModule> providedModules) {
    this.modules = Objects.requireNonNull(modules, "modules");
    this.packages = Objects.requireNonNull(packages, "packages");
    this.jars = Objects.requireNonNull(jars, "jars");
    this.progress = Objects.requireNonNull(progress, "progress");
    this.inputs = inputs;
    this.providedModules =
        providedModules.stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    module -> module.descriptor().coordinate(),
                    java.util.function.Function.identity()));
  }

  ResolvedProjectModule load(SourceFile moduleSource, Map<Path, SourceFile> overlays)
      throws IOException {
    return load(moduleSource, overlays, false);
  }

  ResolvedProjectModule load(
      SourceFile moduleSource, Map<Path, SourceFile> overlays, boolean includeTests)
      throws IOException {
    inputs.source(moduleSource);
    inputs.directory(moduleSource.path().getParent(), true);
    inputs.directory(moduleSource.path().getParent().resolve("resources"), false);
    ModuleDeclaration declaration = modules.evaluate(moduleSource);
    ModuleDescriptor descriptor =
        resolveDeclaration(
            declaration,
            declaration.name().isPresent()
                ? Optional.empty()
                : ModuleNameInference.infer(moduleSource, overlays, declaration.layout()));
    Path root = sourceRoot(moduleSource, descriptor);
    ModuleSourceFiles selected =
        ModuleSourceFiles.load(
            moduleSource, descriptor.name(), declaration.layout(), overlays, includeTests);
    selected.sources().values().forEach(inputs::source);
    Map<String, SourceFile> sources = new LinkedHashMap<>(selected.sources());
    Optional<ResolvedJarBinding> binding = Optional.empty();
    Set<DocumentId> bindingSources = Set.of();
    if (descriptor.binding().isPresent()) {
      if (!isPinned(descriptor.binding().orElseThrow())) {
        throw new IOException(
            "JAR binding is not pinned; run 'norm resolve' for " + moduleSource.path());
      }
      Path moduleRoot = normalize(moduleSource.path()).getParent();
      if (descriptor.binding().orElseThrow().target() instanceof LocalJarTarget target)
        inputs.candidate(moduleRoot.resolve(target.path()));
      ResolvedJarGraph graph = jars.resolve(moduleRoot, descriptor.binding().orElseThrow());
      inputs.graph(graph);
      Map<String, dev.w0fv1.norm.execution.JarBindingClassReference.Nominal> imports =
          new LinkedHashMap<>();
      providedModules
          .values()
          .forEach(
              module ->
                  imports.putAll(
                      module.binding().generated().exportedClasses(module.descriptor().exports())));
      ResolvedJarBinding resolvedBinding =
          JarBindingPreparer.prepare(
              descriptor,
              graph,
              imports,
              providedModules.values().stream().map(module -> module.binding().graph()).toList());
      GeneratedJarBinding generated = resolvedBinding.generated();
      Set<DocumentId> generatedDocuments = new LinkedHashSet<>();
      for (GeneratedBindingSource source : generated.sources()) {
        Path path = normalize(root.resolve(source.relativePath()));
        SourceFile generatedSource = SourceFile.of(path, source.text());
        if (sources.putIfAbsent(source.relativePath(), generatedSource) != null) {
          throw new IOException("JAR binding source conflicts with " + source.relativePath());
        }
        generatedDocuments.add(generatedSource.id());
      }
      bindingSources = Set.copyOf(generatedDocuments);
      binding = Optional.of(resolvedBinding);
    }
    ModuleLoader.LoadedModule loaded =
        new ModuleLoader().load(new ModuleSourceSnapshot(sources), descriptor);
    if (loaded.exportedSources().stream().anyMatch(selected.tests()::contains)) {
      throw new IOException("test sources cannot be exported by a module");
    }
    Map<String, ModuleResource> resources =
        collectResources(normalize(moduleSource.path()).getParent());
    return new ResolvedProjectModule(
        normalize(root),
        moduleSource,
        descriptor,
        loaded.sources(),
        loaded.exportedSources(),
        bindingSources,
        binding,
        resources,
        Optional.empty(),
        selected.tests());
  }

  ModuleDescriptor resolveDeclaration(ModuleDeclaration declaration, Optional<String> inferredName)
      throws IOException {
    String name =
        declaration
            .name()
            .or(() -> inferredName)
            .orElseThrow(
                () -> new IOException("module name cannot be inferred; declare name explicitly"));
    List<ModuleRequirement> dependencies = new java.util.ArrayList<>();
    for (ModuleDependency dependency : declaration.dependencies()) {
      modules.checkpoint();
      progress.accept(
          "Resolving dependency: "
              + dependency.repository().value()
              + ":"
              + dependency.name()
              + "@"
              + (dependency.version().isPresent() ? dependency.version().getAsInt() : "latest"));
      var provided =
          providedModules.values().stream()
              .filter(module -> module.descriptor().name().equals(dependency.name()))
              .findFirst();
      if (provided.isPresent()) {
        var coordinate = provided.orElseThrow().descriptor().coordinate();
        if (!dependency.repository().value().equals("norm")
            || dependency.version().isPresent()
                && dependency.version().getAsInt() != coordinate.version())
          throw new IOException("provided module coordinate does not match " + dependency.name());
        dependencies.add(dependency.resolved(coordinate.version()));
      } else {
        dependencies.add(modules.resolve(dependency, packages));
      }
    }
    return new ModuleDescriptor(
        new ModuleCoordinate(name, declaration.version().orElse(0)),
        declaration.exports(),
        dependencies,
        declaration.binding());
  }

  static Map<String, ModuleResource> collectResources(Path moduleRoot) throws IOException {
    Path root = normalize(moduleRoot.resolve("resources"));
    if (!Files.isDirectory(root)) return Map.of();
    Map<String, ModuleResource> resources = new LinkedHashMap<>();
    try (var paths = Files.walk(root)) {
      for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
        String relative = relativePath(root, path);
        resources.put(relative, new ModuleResource(relative, Files.readAllBytes(path)));
      }
    }
    return Map.copyOf(resources);
  }

  private static boolean isPinned(JarBinding binding) {
    return switch (binding.target()) {
      case LocalJarTarget target -> target.integrity().isPresent();
      case MavenJarTarget target -> target.resolution().isPresent();
      case JdkModuleTarget target -> target.resolution().isPresent();
    };
  }
}
