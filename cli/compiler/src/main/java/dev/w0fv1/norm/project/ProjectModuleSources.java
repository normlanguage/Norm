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
  private final ModuleEvaluator modules;
  private final NormPackageResolver packages;
  private final JarResolver jars;
  private final java.util.function.Consumer<String> progress;

  ProjectModuleSources(
      ModuleEvaluator modules,
      NormPackageResolver packages,
      JarResolver jars,
      java.util.function.Consumer<String> progress) {
    this.modules = Objects.requireNonNull(modules, "modules");
    this.packages = Objects.requireNonNull(packages, "packages");
    this.jars = Objects.requireNonNull(jars, "jars");
    this.progress = Objects.requireNonNull(progress, "progress");
  }

  ResolvedProjectModule load(SourceFile moduleSource, Map<Path, SourceFile> overlays)
      throws IOException {
    return load(moduleSource, overlays, false);
  }

  ResolvedProjectModule load(
      SourceFile moduleSource, Map<Path, SourceFile> overlays, boolean includeTests)
      throws IOException {
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
    Map<String, SourceFile> sources = new LinkedHashMap<>(selected.sources());
    Optional<ResolvedJarBinding> binding = Optional.empty();
    Set<DocumentId> bindingSources = Set.of();
    if (descriptor.binding().isPresent()) {
      if (!isPinned(descriptor.binding().orElseThrow())) {
        throw new IOException(
            "JAR binding is not pinned; run 'norm resolve' for " + moduleSource.path());
      }
      Path moduleRoot = normalize(moduleSource.path()).getParent();
      ResolvedJarGraph graph = jars.resolve(moduleRoot, descriptor.binding().orElseThrow());
      ResolvedJarBinding resolvedBinding = JarBindingPreparer.prepare(descriptor, graph);
      GeneratedJarBinding generated = resolvedBinding.generated();
      List<String> exports = new java.util.ArrayList<>(generated.exports());
      exports.addAll(
          descriptor
              .exports()
              .subList(
                  descriptor.binding().orElseThrow().api().size(), descriptor.exports().size()));
      descriptor = descriptor.withExports(exports);
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
        ResolvedProjectModule.exportedSources(loaded, bindingSources),
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
      progress.accept(
          "Resolving dependency: "
              + dependency.repository().value()
              + ":"
              + dependency.name()
              + "@"
              + (dependency.version().isPresent() ? dependency.version().getAsInt() : "latest"));
      dependencies.add(packages.resolve(dependency));
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
    };
  }
}
