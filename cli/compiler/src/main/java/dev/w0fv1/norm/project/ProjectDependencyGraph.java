package dev.w0fv1.norm.project;

import static dev.w0fv1.norm.project.ProjectPaths.normalize;
import static dev.w0fv1.norm.project.ProjectPaths.repositoryRoot;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleRepositoryId;
import dev.w0fv1.norm.value.ModuleRequirement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ProjectDependencyGraph {
  private final ProjectModuleSources moduleSources;
  private final ArchivedModuleLoader archives;
  private final Set<String> reservedModuleNames;

  ProjectDependencyGraph(
      ProjectModuleSources moduleSources,
      ArchivedModuleLoader archives,
      Set<String> reservedModuleNames) {
    this.moduleSources = Objects.requireNonNull(moduleSources, "moduleSources");
    this.archives = Objects.requireNonNull(archives, "archives");
    this.reservedModuleNames = Set.copyOf(reservedModuleNames);
  }

  List<ResolvedProjectModule> resolve(
      ResolvedProjectModule rootModule, Map<Path, SourceFile> overlays, ProjectLoadPurpose purpose)
      throws IOException {
    return new Traversal(rootModule, overlays, purpose).resolve();
  }

  void requireAvailableModuleName(ModuleDescriptor descriptor) throws IOException {
    if (descriptor.name().startsWith("__") || reservedModuleNames.contains(descriptor.name())) {
      throw new IOException("module name '" + descriptor.name() + "' is reserved");
    }
  }

  private static void validatePackageOwnership(List<ResolvedProjectModule> graph)
      throws IOException {
    Map<String, ModuleCoordinate> owners = new LinkedHashMap<>();
    for (ResolvedProjectModule module : graph) {
      for (String path : module.sources().keySet()) {
        String packageName = parent(path).replace('/', '.');
        ModuleCoordinate previous =
            owners.putIfAbsent(packageName, module.descriptor().coordinate());
        if (previous != null && !previous.equals(module.descriptor().coordinate())) {
          throw new IOException(
              "package '"
                  + packageName
                  + "' is owned by both "
                  + previous.name()
                  + "@"
                  + previous.version()
                  + " and "
                  + module.descriptor().name()
                  + "@"
                  + module.descriptor().version());
        }
      }
    }
  }

  private static String parent(String path) {
    int separator = path.lastIndexOf('/');
    return separator < 0 ? "" : path.substring(0, separator);
  }

  private final class Traversal {
    private final ResolvedProjectModule rootModule;
    private final Path repositoryRoot;
    private final Map<Path, SourceFile> overlays;
    private final ProjectLoadPurpose purpose;
    private final Map<ModuleCoordinate, ResolvedProjectModule> repository = new LinkedHashMap<>();
    private final Map<ModuleCoordinate, ResolvedProjectModule> resolved = new LinkedHashMap<>();
    private final Map<String, ModuleCoordinate> versions = new LinkedHashMap<>();
    private final Map<ModuleCoordinate, ModuleRepositoryId> repositories = new LinkedHashMap<>();
    private final LinkedHashSet<ModuleCoordinate> visiting = new LinkedHashSet<>();
    private final List<ResolvedProjectModule> ordered = new java.util.ArrayList<>();

    private Traversal(
        ResolvedProjectModule rootModule,
        Map<Path, SourceFile> overlays,
        ProjectLoadPurpose purpose) {
      this.rootModule = Objects.requireNonNull(rootModule, "rootModule");
      this.repositoryRoot = repositoryRoot(rootModule.root());
      this.overlays = Map.copyOf(overlays);
      this.purpose = Objects.requireNonNull(purpose, "purpose");
      repository.put(rootModule.descriptor().coordinate(), rootModule);
    }

    private List<ResolvedProjectModule> resolve() throws IOException {
      visit(rootModule);
      validatePackageOwnership(ordered);
      return List.copyOf(ordered);
    }

    private void visit(ResolvedProjectModule module) throws IOException {
      ModuleCoordinate coordinate = module.descriptor().coordinate();
      ModuleCoordinate selected = versions.putIfAbsent(coordinate.name(), coordinate);
      if (selected != null && !selected.equals(coordinate)) {
        throw new IOException(
            "module graph selects both "
                + selected.name()
                + "@"
                + selected.version()
                + " and "
                + coordinate.name()
                + "@"
                + coordinate.version());
      }
      if (resolved.containsKey(coordinate)) return;
      if (!visiting.add(coordinate)) {
        throw new IOException(
            "cyclic module dependency: "
                + java.util.stream.Stream.concat(
                        visiting.stream(), java.util.stream.Stream.of(coordinate))
                    .map(value -> value.name() + "@" + value.version())
                    .collect(java.util.stream.Collectors.joining(" -> ")));
      }
      for (ModuleRequirement requirement : module.descriptor().dependencies()) {
        var selectedRepository =
            repositories.putIfAbsent(requirement.coordinate(), requirement.repository());
        if (selectedRepository != null && !selectedRepository.equals(requirement.repository())) {
          throw new IOException(
              "module dependency "
                  + requirement.name()
                  + "@"
                  + requirement.version()
                  + " is selected from both '"
                  + selectedRepository.value()
                  + "' and '"
                  + requirement.repository().value()
                  + "'");
        }
        ResolvedProjectModule dependency = dependency(requirement);
        requireAvailableModuleName(dependency.descriptor());
        visit(dependency);
      }
      visiting.remove(coordinate);
      resolved.put(coordinate, module);
      ordered.add(module);
    }

    private ResolvedProjectModule dependency(ModuleRequirement requirement) throws IOException {
      ResolvedProjectModule cached = repository.get(requirement.coordinate());
      if (cached != null) return cached;
      Path dependencyRoot =
          normalize(
              repositoryRoot
                  .resolve("dependencies")
                  .resolve(requirement.name().replace('.', java.io.File.separatorChar)));
      Path modulePath = dependencyRoot.resolve("module.norm");
      SourceFile moduleSource = overlays.get(modulePath);
      if (moduleSource == null) {
        if (!Files.isRegularFile(modulePath)) {
          ResolvedProjectModule archived = archives.load(repositoryRoot, requirement, purpose);
          repository.put(requirement.coordinate(), archived);
          return archived;
        }
        moduleSource = SourceFile.read(modulePath);
      }
      if (!ModuleSourceFiles.isModuleSource(moduleSource)) {
        throw new IOException("dependency configuration must be module.norm: " + modulePath);
      }
      ResolvedProjectModule resolved = moduleSources.load(moduleSource, overlays);
      if (!resolved.descriptor().coordinate().equals(requirement.coordinate())) {
        throw new IOException(
            "module dependency '"
                + requirement.name()
                + "@"
                + requirement.version()
                + "' resolved to "
                + resolved.descriptor().name()
                + "@"
                + resolved.descriptor().version());
      }
      repository.put(requirement.coordinate(), resolved);
      return resolved;
    }
  }
}
