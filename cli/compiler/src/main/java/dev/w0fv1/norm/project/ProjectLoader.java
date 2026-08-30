package dev.w0fv1.norm.project;

import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.frontend.ModuleLoader;
import dev.w0fv1.norm.frontend.ModuleSourceResolver;
import dev.w0fv1.norm.frontend.SourceHeader;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleGraph;
import dev.w0fv1.norm.value.ModuleRequirement;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ProjectLoader implements AutoCloseable {
  private final ModuleEvaluator modules;
  private final Set<String> reservedModuleNames;

  ProjectLoader(ModuleEvaluator modules, Set<String> reservedModuleNames) {
    this.modules = Objects.requireNonNull(modules, "modules");
    this.reservedModuleNames = Set.copyOf(reservedModuleNames);
  }

  public ProjectSourceSet load(Path entryPath) throws IOException {
    return load(SourceFile.read(normalize(entryPath)), List.of());
  }

  public ProjectSourceSet load(SourceFile entrySource, Collection<SourceFile> overlays)
      throws IOException {
    Objects.requireNonNull(entrySource, "entrySource");
    Map<Path, SourceFile> overlaySources = overlaySources(entrySource, overlays);
    Path entry = normalize(entrySource.path());
    ProjectLocation location = locate(entry, overlaySources);
    if (location.module().isEmpty()) {
      return new ProjectSourceSet(
          location.standaloneRoot(),
          entry,
          Optional.empty(),
          Set.of(),
          CompilationScope.anonymous(List.of(entrySource)),
          List.of(entrySource),
          Set.of());
    }

    SourceFile moduleSource = location.module().orElseThrow();
    Path modulePath = normalize(moduleSource.path());
    if (entry.equals(modulePath)) {
      throw new IOException("module.norm is project configuration, not an application entry");
    }
    ResolvedModule rootModule = resolveModule(moduleSource, overlaySources);
    requireAvailableModuleName(rootModule.descriptor());
    Path root = rootModule.root();
    if (!rootModule.sources().containsKey(relativePath(root, entry))) {
      throw new IOException("entry source is not part of the module");
    }
    List<ResolvedModule> graph = resolveGraph(rootModule, overlaySources);
    validatePackageOwnership(graph);
    List<SourceFile> sources = new java.util.ArrayList<>();
    Set<Path> exportedSources = new LinkedHashSet<>();
    Set<Path> modulePaths = new LinkedHashSet<>();
    Map<DocumentId, ModuleSourceCoordinate> coordinates = new LinkedHashMap<>();
    Map<ModuleCoordinate, Set<ModuleCoordinate>> dependencies = new LinkedHashMap<>();
    for (ResolvedModule module : graph) {
      dependencies.put(
          module.descriptor().coordinate(),
          module.descriptor().dependencies().stream()
              .map(ModuleRequirement::coordinate)
              .collect(java.util.stream.Collectors.toSet()));
      modulePaths.add(normalize(module.moduleSource().path()));
      exportedSources.addAll(
          module.exportedSources().stream()
              .map(DocumentId::uri)
              .map(Path::of)
              .map(ProjectLoader::normalize)
              .toList());
      for (Map.Entry<String, SourceFile> source : module.sources().entrySet()) {
        sources.add(source.getValue());
        coordinates.put(
            source.getValue().id(),
            new ModuleSourceCoordinate(module.descriptor().coordinate(), source.getKey()));
      }
    }
    return new ProjectSourceSet(
        root,
        entry,
        Optional.of(modulePath),
        modulePaths,
        new CompilationScope(coordinates, new ModuleGraph(dependencies)),
        sources,
        exportedSources);
  }

  private List<ResolvedModule> resolveGraph(
      ResolvedModule rootModule, Map<Path, SourceFile> overlays) throws IOException {
    Map<ModuleCoordinate, ResolvedModule> resolved = new LinkedHashMap<>();
    Map<ModuleCoordinate, ResolvedModule> repository = new LinkedHashMap<>();
    repository.put(rootModule.descriptor().coordinate(), rootModule);
    Map<String, ModuleCoordinate> versions = new LinkedHashMap<>();
    LinkedHashSet<ModuleCoordinate> visiting = new LinkedHashSet<>();
    List<ResolvedModule> ordered = new java.util.ArrayList<>();
    resolveDependencies(
        rootModule,
        repositoryRoot(rootModule),
        overlays,
        repository,
        resolved,
        versions,
        visiting,
        ordered);
    return List.copyOf(ordered);
  }

  private static void validatePackageOwnership(List<ResolvedModule> graph) throws IOException {
    Map<String, ModuleCoordinate> owners = new LinkedHashMap<>();
    for (ResolvedModule module : graph) {
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

  private void resolveDependencies(
      ResolvedModule module,
      Path repositoryRoot,
      Map<Path, SourceFile> overlays,
      Map<ModuleCoordinate, ResolvedModule> repository,
      Map<ModuleCoordinate, ResolvedModule> resolved,
      Map<String, ModuleCoordinate> versions,
      LinkedHashSet<ModuleCoordinate> visiting,
      List<ResolvedModule> ordered)
      throws IOException {
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
      ResolvedModule dependency =
          resolveDependency(repositoryRoot, requirement, overlays, repository);
      requireAvailableModuleName(dependency.descriptor());
      resolveDependencies(
          dependency, repositoryRoot, overlays, repository, resolved, versions, visiting, ordered);
    }
    visiting.remove(coordinate);
    resolved.put(coordinate, module);
    ordered.add(module);
  }

  private ResolvedModule resolveDependency(
      Path repositoryRoot,
      ModuleRequirement requirement,
      Map<Path, SourceFile> overlays,
      Map<ModuleCoordinate, ResolvedModule> repository)
      throws IOException {
    ResolvedModule cached = repository.get(requirement.coordinate());
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
        throw new IOException(
            "cannot resolve module dependency '"
                + requirement.name()
                + "@"
                + requirement.version()
                + "' from "
                + repositoryRoot);
      }
      moduleSource = SourceFile.read(modulePath);
    }
    if (!isModuleSource(moduleSource)) {
      throw new IOException("dependency configuration must be module.norm: " + modulePath);
    }
    ResolvedModule resolved = resolveModule(moduleSource, overlays);
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

  private void requireAvailableModuleName(ModuleDescriptor descriptor) throws IOException {
    if (reservedModuleNames.contains(descriptor.name())) {
      throw new IOException("module name '" + descriptor.name() + "' is reserved");
    }
  }

  private ResolvedModule resolveModule(SourceFile moduleSource, Map<Path, SourceFile> overlays)
      throws IOException {
    ModuleDescriptor descriptor = modules.evaluate(moduleSource);
    Path root = sourceRoot(moduleSource, descriptor);
    Map<String, SourceFile> sources = collectSources(root, moduleSource, overlays);
    ModuleLoader.LoadedModule loaded =
        new ModuleLoader().load(new MemoryResolver(sources), descriptor);
    return new ResolvedModule(
        normalize(root), moduleSource, descriptor, loaded.sources(), loaded.exportedSources());
  }

  public Path projectRoot(SourceFile source, Collection<SourceFile> overlays) {
    Objects.requireNonNull(source, "source");
    Path path = normalize(source.path());
    ProjectLocation location;
    try {
      location = locate(path, overlaySources(source, overlays));
    } catch (IOException exception) {
      Path parent = path.getParent();
      if (parent == null) throw new IllegalArgumentException("source path has no parent");
      return parent;
    }
    if (location.module().isEmpty()) return location.standaloneRoot();
    try {
      SourceFile moduleSource = location.module().orElseThrow();
      return sourceRoot(moduleSource, modules.evaluate(moduleSource));
    } catch (IOException exception) {
      return normalize(location.module().orElseThrow().path()).getParent();
    }
  }

  public ModuleDescriptor evaluateModule(SourceFile source) throws IOException {
    if (!isModuleSource(source)) {
      throw new IllegalArgumentException("source is not a module configuration");
    }
    return modules.evaluate(source);
  }

  public CompilationSnapshot analyzeModule(SourceFile source) {
    if (!isModuleSource(source)) {
      throw new IllegalArgumentException("source is not a module configuration");
    }
    return modules.snapshot(source);
  }

  public static boolean isModuleSource(SourceFile source) {
    Objects.requireNonNull(source, "source");
    Path path = source.path();
    return path.getFileName().toString().equals("module.norm")
        && SourceHeader.parse(source).packageName().isEmpty();
  }

  @Override
  public void close() {
    modules.close();
  }

  private static Map<String, SourceFile> collectSources(
      Path root, SourceFile moduleSource, Map<Path, SourceFile> overlays) throws IOException {
    Path modulePath = normalize(moduleSource.path());
    Map<String, SourceFile> sources = new LinkedHashMap<>();
    List<Path> diskSources = List.of();
    if (Files.isDirectory(root)) {
      try (var paths = Files.walk(root)) {
        diskSources =
            paths
                .filter(Files::isRegularFile)
                .filter(ProjectLoader::isNormSource)
                .map(ProjectLoader::normalize)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
      }
    }
    Set<Path> nestedRoots = nestedModuleRoots(root, modulePath, diskSources, overlays);
    for (Path path : diskSources) {
      if (!path.equals(modulePath) && !insideNestedModule(path, nestedRoots)) {
        SourceFile overlay = overlays.get(path);
        sources.put(relativePath(root, path), overlay == null ? SourceFile.read(path) : overlay);
      }
    }
    overlays.entrySet().stream()
        .filter(source -> source.getKey().startsWith(root))
        .filter(source -> isNormSource(source.getKey()))
        .filter(source -> !source.getKey().equals(modulePath))
        .filter(source -> !insideNestedModule(source.getKey(), nestedRoots))
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(Path::toString)))
        .forEach(source -> sources.put(relativePath(root, source.getKey()), source.getValue()));
    return sources;
  }

  private static ProjectLocation locate(Path entry, Map<Path, SourceFile> overlays)
      throws IOException {
    Path fallback = entry.getParent();
    if (fallback == null) throw new IllegalArgumentException("source path has no parent");
    Path current = fallback;
    while (current != null) {
      Path candidate = normalize(current.resolve("module.norm"));
      SourceFile overlay = overlays.get(candidate);
      if (overlay != null && isModuleSource(overlay)) {
        return new ProjectLocation(current, Optional.of(overlay));
      }
      if (overlay == null && Files.isRegularFile(candidate)) {
        SourceFile source = SourceFile.read(candidate);
        if (isModuleSource(source)) return new ProjectLocation(current, Optional.of(source));
      }
      current = current.getParent();
    }
    return new ProjectLocation(fallback, Optional.empty());
  }

  private static Map<Path, SourceFile> overlaySources(
      SourceFile entrySource, Collection<SourceFile> overlays) {
    Objects.requireNonNull(overlays, "overlays");
    Map<Path, SourceFile> sources = new LinkedHashMap<>();
    for (SourceFile overlay : overlays) sources.put(normalize(overlay.path()), overlay);
    sources.put(normalize(entrySource.path()), entrySource);
    return sources;
  }

  private static Set<Path> nestedModuleRoots(
      Path root, Path modulePath, List<Path> diskSources, Map<Path, SourceFile> overlays)
      throws IOException {
    Map<Path, SourceFile> candidates = new LinkedHashMap<>();
    for (Path path : diskSources) {
      if (!path.equals(modulePath) && path.getFileName().toString().equals("module.norm")) {
        SourceFile overlay = overlays.get(path);
        candidates.put(path, overlay == null ? SourceFile.read(path) : overlay);
      }
    }
    overlays.entrySet().stream()
        .filter(source -> source.getKey().startsWith(root))
        .filter(source -> !source.getKey().equals(modulePath))
        .filter(source -> source.getKey().getFileName().toString().equals("module.norm"))
        .forEach(source -> candidates.put(source.getKey(), source.getValue()));
    List<Path> candidateRoots =
        candidates.entrySet().stream()
            .filter(source -> isModuleSource(source.getValue()))
            .map(source -> source.getKey().getParent())
            .sorted(
                Comparator.comparingInt(Path::getNameCount)
                    .thenComparing(Comparator.comparing(Path::toString)))
            .toList();
    Set<Path> nestedRoots = new LinkedHashSet<>();
    for (Path candidate : candidateRoots) {
      if (!insideNestedModule(candidate, nestedRoots)) nestedRoots.add(candidate);
    }
    return Set.copyOf(nestedRoots);
  }

  private static boolean insideNestedModule(Path path, Set<Path> nestedRoots) {
    return nestedRoots.stream().anyMatch(path::startsWith);
  }

  private static String relativePath(Path root, Path path) {
    return root.relativize(path).toString().replace('\\', '/');
  }

  private static String parent(String path) {
    int separator = path.lastIndexOf('/');
    return separator < 0 ? "" : path.substring(0, separator);
  }

  private static boolean isNormSource(Path path) {
    return path.getFileName().toString().endsWith(".norm");
  }

  private static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }

  private static Path sourceRoot(SourceFile moduleSource, ModuleDescriptor descriptor)
      throws IOException {
    Path moduleRoot = normalize(moduleSource.path()).getParent();
    if (moduleRoot == null) throw new IOException("module configuration path has no parent");
    Path current = moduleRoot;
    String[] segments = descriptor.name().split("\\.");
    for (int index = segments.length - 1; index >= 0; index--) {
      Path name = current.getFileName();
      if (name == null || !name.toString().equals(segments[index])) {
        throw new IOException(
            "module configuration directory must match module name '"
                + descriptor.name()
                + "': "
                + moduleSource.path());
      }
      current = current.getParent();
      if (current == null) {
        throw new IOException("module configuration path has no source root");
      }
    }
    return normalize(current);
  }

  private static Path repositoryRoot(ResolvedModule module) {
    Path root = module.root();
    Path name = root.getFileName();
    if (name != null && name.toString().equals("dependencies") && root.getParent() != null) {
      return root.getParent();
    }
    return root;
  }

  private record ProjectLocation(Path standaloneRoot, Optional<SourceFile> module) {}

  private record ResolvedModule(
      Path root,
      SourceFile moduleSource,
      ModuleDescriptor descriptor,
      Map<String, SourceFile> sources,
      Set<DocumentId> exportedSources) {
    private ResolvedModule {
      root = normalize(root);
      Objects.requireNonNull(moduleSource, "moduleSource");
      Objects.requireNonNull(descriptor, "descriptor");
      sources = Map.copyOf(sources);
      exportedSources = Set.copyOf(exportedSources);
    }
  }

  private record MemoryResolver(Map<String, SourceFile> sources) implements ModuleSourceResolver {
    private MemoryResolver {
      sources = Map.copyOf(sources);
    }

    @Override
    public SourceFile read(String relativePath) throws IOException {
      SourceFile source = sources.get(relativePath);
      if (source == null) throw new IOException("source '" + relativePath + "' does not exist");
      return source;
    }

    @Override
    public List<String> listSources() {
      return List.copyOf(sources.keySet());
    }
  }
}
