package dev.w0fv1.norm.project;

import static dev.w0fv1.norm.project.ProjectPaths.normalize;
import static dev.w0fv1.norm.project.ProjectPaths.repositoryRoot;
import static dev.w0fv1.norm.project.ProjectPaths.sourceRoot;

import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.frontend.ModuleLoader;
import dev.w0fv1.norm.frontend.SourceHeader;
import dev.w0fv1.norm.frontend.SourceStructure;
import dev.w0fv1.norm.jvm.JarResolver;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.packages.NormPackageResolver;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDeclaration;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleGraph;
import dev.w0fv1.norm.value.ModuleRequirement;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
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
  private final JarResolver jars;
  private final NormPackageResolver packages;
  private final ProjectModuleSources moduleSources;
  private final ArchivedModuleLoader archivedModules;
  private final ProjectDependencyGraph dependencies;
  private final ProjectInputTracker inputs = new ProjectInputTracker();

  public ProjectInputSnapshot inputSnapshot(ProjectSourceSet sources) {
    var captured = inputs.snapshot(sources);
    var resolution = packages.resolutionInputs();
    var files = new java.util.ArrayList<>(captured.files());
    files.addAll(resolution.files());
    var absent = new java.util.ArrayList<>(captured.absent());
    absent.addAll(resolution.absent());
    return new ProjectInputSnapshot(files, captured.directories(), absent);
  }

  public List<ModuleEvaluation> moduleEvaluations() {
    return modules.evaluations();
  }

  public void replayModules(List<ModuleEvaluation> evaluations) {
    modules.replay(evaluations);
  }

  ProjectLoader(ModuleEvaluator modules, Set<String> reservedModuleNames) {
    this(modules, reservedModuleNames, message -> {});
  }

  ProjectLoader(
      ModuleEvaluator modules,
      Set<String> reservedModuleNames,
      java.util.function.Consumer<String> progress) {
    this(
        modules,
        reservedModuleNames,
        new NormPackageResolver(defaultCache().resolve("packages")),
        new JarResolver(defaultCache().resolve("maven"), progress),
        progress);
  }

  ProjectLoader(
      ModuleEvaluator modules,
      Set<String> reservedModuleNames,
      NormPackageResolver packages,
      JarResolver jars) {
    this(modules, reservedModuleNames, packages, jars, message -> {});
  }

  private ProjectLoader(
      ModuleEvaluator modules,
      Set<String> reservedModuleNames,
      NormPackageResolver packages,
      JarResolver jars,
      java.util.function.Consumer<String> progress) {
    this.modules = Objects.requireNonNull(modules, "modules");
    this.packages = Objects.requireNonNull(packages, "packages");
    this.jars = Objects.requireNonNull(jars, "jars");
    this.moduleSources =
        new ProjectModuleSources(this.modules, this.packages, this.jars, progress, inputs);
    this.archivedModules = new ArchivedModuleLoader(this.packages, this.jars, progress);
    this.dependencies =
        new ProjectDependencyGraph(moduleSources, archivedModules, reservedModuleNames, inputs);
  }

  public ProjectSourceSet load(Path entryPath) throws IOException {
    return load(SourceFile.read(normalize(entryPath)), List.of());
  }

  public ProjectSourceSet loadForTests(Path entryPath) throws IOException {
    return load(entryPath, ProjectLoadPurpose.TEST);
  }

  public ProjectSourceSet loadForAnalysis(Path entryPath) throws IOException {
    return load(entryPath, ProjectLoadPurpose.ANALYSIS);
  }

  public ModuleRequirement resolveReference(
      dev.w0fv1.norm.value.ModuleRepositoryId repository, String path, int version)
      throws IOException {
    return packages.resolveReference(repository, path, version);
  }

  public ProjectSourceSet loadForAnalysis(Path workspace, ModuleRequirement requirement)
      throws IOException {
    Path root = normalize(workspace);
    ResolvedProjectModule module =
        archivedModules.load(root, requirement, ProjectLoadPurpose.ANALYSIS);
    dependencies.requireAvailableModuleName(module.descriptor());
    SourceFile entry =
        module.sources().values().stream()
            .min(Comparator.comparing(source -> source.path().toString()))
            .orElseThrow(() -> new IOException("module contains no source files"));
    var graph = dependencies.resolve(module, Map.of(), ProjectLoadPurpose.ANALYSIS);
    return sourceSet(
        root, entry.path(), module.moduleSource().path(), graph, SourceStructure.inspect(entry));
  }

  private ProjectSourceSet load(Path entryPath, ProjectLoadPurpose purpose) throws IOException {
    Path entry = normalize(entryPath);
    if (Files.isDirectory(entry)) {
      SourceFile module = SourceFile.read(entry.resolve("module.norm"));
      ResolvedProjectModule resolved = moduleSources.load(module, Map.of(), true);
      SourceFile first =
          resolved.sources().values().stream()
              .min(Comparator.comparing(source -> source.path().toString()))
              .orElseThrow(() -> new IOException("module contains no source files"));
      return loadResolvedModule(
          resolved, first, module.path(), Map.of(), purpose, SourceStructure.inspect(first));
    }
    return load(SourceFile.read(entry), List.of(), purpose);
  }

  public ProjectSourceSet load(SourceFile entrySource, Collection<SourceFile> overlays)
      throws IOException {
    return load(entrySource, overlays, ProjectLoadPurpose.RUNTIME);
  }

  public ProjectSourceSet loadForAnalysis(SourceFile entrySource, Collection<SourceFile> overlays)
      throws IOException {
    return load(entrySource, overlays, ProjectLoadPurpose.ANALYSIS);
  }

  private ProjectSourceSet load(
      SourceFile entrySource, Collection<SourceFile> overlays, ProjectLoadPurpose purpose)
      throws IOException {
    Objects.requireNonNull(entrySource, "entrySource");
    Objects.requireNonNull(purpose, "purpose");
    inputs.clear();
    packages.clearResolutionInputs();
    modules.clearEvaluations();
    inputs.source(entrySource);
    Map<Path, SourceFile> overlaySources = overlaySources(entrySource, overlays);
    Path entry = normalize(entrySource.path());
    ProjectLocation location = locate(entry, overlaySources);
    SourceStructure entryStructure = SourceStructure.inspect(entrySource);
    if (location.module().isEmpty()) {
      if (entryStructure.moduleConfiguration().isPresent()) {
        return loadEmbeddedModule(entrySource, overlaySources, purpose, entryStructure);
      }
      return new ProjectSourceSet(
          location.standaloneRoot(),
          entry,
          Optional.empty(),
          Set.of(),
          Map.of(),
          Map.of(),
          CompilationScope.anonymous(List.of(entrySource)),
          List.of(entrySource),
          Set.of(),
          Set.of(),
          Map.of(),
          new ProjectResources(Map.of()),
          entryStructure.applicationFactory(),
          entryStructure.mainEntrypoint());
    }

    SourceFile moduleSource = location.module().orElseThrow();
    Path modulePath = normalize(moduleSource.path());
    if (entry.equals(modulePath)) {
      throw new IOException("module.norm is project configuration, not an application entry");
    }
    ResolvedProjectModule rootModule =
        moduleSources.load(moduleSource, overlaySources, purpose != ProjectLoadPurpose.RUNTIME);
    return loadResolvedModule(
        rootModule, entrySource, modulePath, overlaySources, purpose, entryStructure);
  }

  private ProjectSourceSet loadResolvedModule(
      ResolvedProjectModule rootModule,
      SourceFile entrySource,
      Path modulePath,
      Map<Path, SourceFile> overlaySources,
      ProjectLoadPurpose purpose,
      SourceStructure entryStructure)
      throws IOException {
    Path entry = normalize(entrySource.path());
    if (purpose == ProjectLoadPurpose.RUNTIME || !rootModule.descriptor().name().equals("std")) {
      dependencies.requireAvailableModuleName(rootModule.descriptor());
    }
    Path root = repositoryRoot(rootModule.root());
    if (rootModule.sources().values().stream()
        .noneMatch(source -> normalize(source.path()).equals(entry))) {
      throw new IOException("entry source is not part of the module");
    }
    List<ResolvedProjectModule> graph = dependencies.resolve(rootModule, overlaySources, purpose);
    return sourceSet(root, entry, modulePath, graph, entryStructure);
  }

  private static ProjectSourceSet sourceSet(
      Path root,
      Path entry,
      Path rootModulePath,
      List<ResolvedProjectModule> graph,
      SourceStructure entryStructure)
      throws IOException {
    List<SourceFile> sources = new java.util.ArrayList<>();
    Set<Path> exportedSources = new LinkedHashSet<>();
    Set<Path> modulePaths = new LinkedHashSet<>();
    Map<DocumentId, ModuleSourceCoordinate> coordinates = new LinkedHashMap<>();
    Map<ModuleCoordinate, Set<ModuleCoordinate>> dependencies = new LinkedHashMap<>();
    Map<ModuleCoordinate, ModuleDescriptor> descriptors =
        graph.stream()
            .map(ResolvedProjectModule::descriptor)
            .collect(
                java.util.stream.Collectors.toMap(
                    ModuleDescriptor::coordinate,
                    java.util.function.Function.identity(),
                    (left, right) -> left,
                    LinkedHashMap::new));
    Map<ModuleCoordinate, FileSnapshot> moduleArchives = new LinkedHashMap<>();
    Set<DocumentId> bindingSources = new LinkedHashSet<>();
    Set<DocumentId> testSources = new LinkedHashSet<>();
    Map<ModuleCoordinate, ResolvedJarBinding> jarBindings = new LinkedHashMap<>();
    Map<ModuleCoordinate, Map<String, ModuleResource>> resources = new LinkedHashMap<>();
    for (ResolvedProjectModule module : graph) {
      module
          .archive()
          .ifPresent(archive -> moduleArchives.put(module.descriptor().coordinate(), archive));
      dependencies.put(
          module.descriptor().coordinate(), readableDependencies(module.descriptor(), descriptors));
      modulePaths.add(normalize(module.moduleSource().path()));
      bindingSources.addAll(module.bindingSources());
      testSources.addAll(module.testSources());
      module
          .binding()
          .ifPresent(binding -> jarBindings.put(module.descriptor().coordinate(), binding));
      resources.put(module.descriptor().coordinate(), module.resources());
      exportedSources.addAll(
          module.exportedSources().stream()
              .map(DocumentId::uri)
              .map(Path::of)
              .map(ProjectPaths::normalize)
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
        Optional.of(rootModulePath),
        modulePaths,
        descriptors,
        moduleArchives,
        new CompilationScope(coordinates, new ModuleGraph(dependencies), testSources),
        sources,
        exportedSources,
        bindingSources,
        jarBindings,
        new ProjectResources(resources),
        entryStructure.applicationFactory(),
        entryStructure.mainEntrypoint());
  }

  private ProjectSourceSet loadEmbeddedModule(
      SourceFile entrySource,
      Map<Path, SourceFile> overlays,
      ProjectLoadPurpose purpose,
      SourceStructure structure)
      throws IOException {
    inputs.directory(entrySource.path().getParent().resolve("resources"), false);
    SourceFile programSource = structure.programSource();
    Optional<String> packageName = SourceHeader.parse(programSource).packageName();
    ModuleDeclaration declaration = modules.evaluate(structure.moduleConfiguration().orElseThrow());
    boolean localApplication = packageName.isEmpty() && declaration.name().isEmpty();
    ModuleDescriptor descriptor =
        moduleSources.resolveDeclaration(
            declaration,
            localApplication
                ? Optional.of(ModuleCoordinate.localApplication().name())
                : packageName);
    if (!localApplication) dependencies.requireAvailableModuleName(descriptor);
    if (descriptor.binding().isPresent()) {
      throw new IOException("an application source cannot declare a Java binding");
    }
    String sourcePackage = packageName.orElse("");
    String modulePackage = descriptor.name();
    if (!sourcePackage.isEmpty()
        && !sourcePackage.equals(modulePackage)
        && !sourcePackage.startsWith(modulePackage + ".")) {
      throw new IOException(
          "single-file module source must declare package '"
              + modulePackage
              + "' or one of its child packages");
    }
    if (sourcePackage.isEmpty() && !descriptor.exports().isEmpty()) {
      throw new IOException("a package-less single-file application cannot export sources");
    }
    String sourcePath =
        sourcePackage.isEmpty()
            ? entrySource.path().getFileName().toString()
            : sourcePackage.replace('.', '/') + "/" + entrySource.path().getFileName().toString();
    Map<String, SourceFile> moduleSources = Map.of(sourcePath, programSource);
    ModuleLoader.LoadedModule loaded =
        sourcePackage.isEmpty()
            ? new ModuleLoader.LoadedModule(descriptor, moduleSources, Set.of())
            : new ModuleLoader().load(new ModuleSourceSnapshot(moduleSources), descriptor);
    Path root = normalize(entrySource.path()).getParent();
    if (root == null) throw new IOException("application source path has no parent");
    ResolvedProjectModule rootModule =
        new ResolvedProjectModule(
            root,
            entrySource,
            descriptor,
            loaded.sources(),
            loaded.exportedSources(),
            Set.of(),
            Optional.empty(),
            ProjectModuleSources.collectResources(root),
            Optional.empty());
    List<ResolvedProjectModule> graph = dependencies.resolve(rootModule, overlays, purpose);
    Path entry = normalize(entrySource.path());
    return sourceSet(root, entry, entry, graph, structure);
  }

  private static Set<ModuleCoordinate> readableDependencies(
      ModuleDescriptor module, Map<ModuleCoordinate, ModuleDescriptor> descriptors) {
    Set<ModuleCoordinate> readable = new LinkedHashSet<>();
    for (ModuleRequirement requirement : module.dependencies()) {
      if (readable.add(requirement.coordinate())) {
        collectExportedDependencies(requirement.coordinate(), descriptors, readable);
      }
    }
    return Set.copyOf(readable);
  }

  private static void collectExportedDependencies(
      ModuleCoordinate coordinate,
      Map<ModuleCoordinate, ModuleDescriptor> descriptors,
      Set<ModuleCoordinate> readable) {
    ModuleDescriptor descriptor = descriptors.get(coordinate);
    if (descriptor == null) return;
    for (ModuleRequirement requirement : descriptor.dependencies()) {
      if (requirement.exported() && readable.add(requirement.coordinate())) {
        collectExportedDependencies(requirement.coordinate(), descriptors, readable);
      }
    }
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
      ModuleDeclaration declaration = modules.evaluate(moduleSource);
      return repositoryRoot(
          sourceRoot(
              moduleSource,
              moduleSources.resolveDeclaration(
                  declaration,
                  declaration.name().isPresent()
                      ? Optional.empty()
                      : ModuleNameInference.infer(
                          moduleSource, overlaySources(source, overlays), declaration.layout()))));
    } catch (IOException exception) {
      return normalize(location.module().orElseThrow().path()).getParent();
    }
  }

  public ModuleDescriptor evaluateModule(SourceFile source) throws IOException {
    if (!ModuleSourceFiles.isModuleSource(source)) {
      throw new IllegalArgumentException("source is not a module configuration");
    }
    ModuleDeclaration declaration = modules.evaluate(source);
    return moduleSources.resolveDeclaration(
        declaration,
        declaration.name().isPresent()
            ? Optional.empty()
            : ModuleNameInference.infer(source, Map.of(), declaration.layout()));
  }

  public ResolvedJarGraph resolveJarBinding(SourceFile source) throws IOException {
    ModuleDescriptor descriptor = evaluateModule(source);
    if (descriptor.binding().isEmpty()) {
      throw new IOException("module does not declare a JAR binding");
    }
    Path moduleRoot = normalize(source.path()).getParent();
    if (moduleRoot == null) throw new IOException("module configuration path has no parent");
    return jars.resolve(moduleRoot, descriptor.binding().orElseThrow());
  }

  public ResolvedJarBinding generateJarBinding(SourceFile source) throws IOException {
    return moduleArchiveContents(source)
        .binding()
        .orElseThrow(() -> new IOException("module does not declare a JAR binding"));
  }

  ModuleArchiveContents moduleArchiveContents(SourceFile source) throws IOException {
    if (!ModuleSourceFiles.isModuleSource(source)) {
      throw new IllegalArgumentException("source is not a module configuration");
    }
    ResolvedProjectModule resolved = moduleSources.load(source, Map.of());
    resolved = dependencies.resolve(resolved, Map.of(), ProjectLoadPurpose.RUNTIME).getLast();
    return new ModuleArchiveContents(
        resolved.descriptor(), resolved.sources(), resolved.binding(), resolved.resources());
  }

  public CompilationSnapshot analyzeModule(SourceFile source) {
    if (!ModuleSourceFiles.isModuleSource(source)) {
      throw new IllegalArgumentException("source is not a module configuration");
    }
    return modules.snapshot(source);
  }

  @Override
  public void close() {
    archivedModules.clear();
    try {
      modules.close();
    } finally {
      try {
        packages.close();
      } finally {
        jars.close();
      }
    }
  }

  private ProjectLocation locate(Path entry, Map<Path, SourceFile> overlays) throws IOException {
    Path fallback = entry.getParent();
    if (fallback == null) throw new IllegalArgumentException("source path has no parent");
    Path current = fallback;
    while (current != null) {
      Path candidate = normalize(current.resolve("module.norm"));
      inputs.candidate(candidate);
      SourceFile overlay = overlays.get(candidate);
      if (overlay != null && ModuleSourceFiles.isModuleSource(overlay)) {
        return new ProjectLocation(current, Optional.of(overlay));
      }
      if (overlay == null && Files.isRegularFile(candidate)) {
        SourceFile source = SourceFile.read(candidate);
        if (ModuleSourceFiles.isModuleSource(source))
          return new ProjectLocation(current, Optional.of(source));
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

  private record ProjectLocation(Path standaloneRoot, Optional<SourceFile> module) {}

  record ModuleArchiveContents(
      ModuleDescriptor descriptor,
      Map<String, SourceFile> sources,
      Optional<ResolvedJarBinding> binding,
      Map<String, ModuleResource> resources) {
    ModuleArchiveContents {
      Objects.requireNonNull(descriptor, "descriptor");
      sources = Map.copyOf(sources);
      Objects.requireNonNull(binding, "binding");
      resources = Map.copyOf(resources);
    }
  }

  private static Path defaultCache() {
    return Path.of(System.getProperty("user.home"), ".norm", "cache");
  }
}
