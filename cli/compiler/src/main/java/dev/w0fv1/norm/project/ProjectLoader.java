package dev.w0fv1.norm.project;

import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.frontend.ModuleLoader;
import dev.w0fv1.norm.frontend.ModuleSourceResolver;
import dev.w0fv1.norm.frontend.SourceHeader;
import dev.w0fv1.norm.frontend.SourceStructure;
import dev.w0fv1.norm.jvm.GeneratedBindingSource;
import dev.w0fv1.norm.jvm.GeneratedJarBinding;
import dev.w0fv1.norm.jvm.JarApiScanner;
import dev.w0fv1.norm.jvm.JarBindingSourceGenerator;
import dev.w0fv1.norm.jvm.JarResolver;
import dev.w0fv1.norm.jvm.NormPackageResolver;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.LocalJarTarget;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDeclaration;
import dev.w0fv1.norm.value.ModuleDependency;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleGraph;
import dev.w0fv1.norm.value.ModuleRepositoryId;
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
  private final JarResolver jars;
  private final NormPackageResolver packages;
  private final Map<Path, ModuleArchiveReader.ArchivedModule> archives =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<AnalysisModuleKey, ResolvedModule> analysisModules =
      new java.util.concurrent.ConcurrentHashMap<>();

  ProjectLoader(ModuleEvaluator modules, Set<String> reservedModuleNames) {
    this(
        modules,
        reservedModuleNames,
        new NormPackageResolver(defaultCache().resolve("packages")),
        new JarResolver(defaultCache().resolve("maven")));
  }

  ProjectLoader(
      ModuleEvaluator modules,
      Set<String> reservedModuleNames,
      NormPackageResolver packages,
      JarResolver jars) {
    this.modules = Objects.requireNonNull(modules, "modules");
    this.reservedModuleNames = Set.copyOf(reservedModuleNames);
    this.packages = Objects.requireNonNull(packages, "packages");
    this.jars = Objects.requireNonNull(jars, "jars");
  }

  public ProjectSourceSet load(Path entryPath) throws IOException {
    return load(SourceFile.read(normalize(entryPath)), List.of());
  }

  public ProjectSourceSet loadForAnalysis(Path entryPath) throws IOException {
    return loadForAnalysis(SourceFile.read(normalize(entryPath)), List.of());
  }

  public ProjectSourceSet load(SourceFile entrySource, Collection<SourceFile> overlays)
      throws IOException {
    return load(entrySource, overlays, LoadPurpose.RUNTIME);
  }

  public ProjectSourceSet loadForAnalysis(SourceFile entrySource, Collection<SourceFile> overlays)
      throws IOException {
    return load(entrySource, overlays, LoadPurpose.ANALYSIS);
  }

  private ProjectSourceSet load(
      SourceFile entrySource, Collection<SourceFile> overlays, LoadPurpose purpose)
      throws IOException {
    Objects.requireNonNull(entrySource, "entrySource");
    Objects.requireNonNull(purpose, "purpose");
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
          CompilationScope.anonymous(List.of(entrySource)),
          List.of(entrySource),
          Set.of(),
          Set.of(),
          List.of(),
          Map.of(),
          entryStructure.applicationFactory(),
          entryStructure.mainEntrypoint());
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
    List<ResolvedModule> graph = resolveGraph(rootModule, overlaySources, purpose);
    validatePackageOwnership(graph);
    return sourceSet(root, entry, modulePath, graph, entryStructure);
  }

  private static ProjectSourceSet sourceSet(
      Path root,
      Path entry,
      Path rootModulePath,
      List<ResolvedModule> graph,
      SourceStructure entryStructure)
      throws IOException {
    List<SourceFile> sources = new java.util.ArrayList<>();
    Set<Path> exportedSources = new LinkedHashSet<>();
    Set<Path> modulePaths = new LinkedHashSet<>();
    Map<DocumentId, ModuleSourceCoordinate> coordinates = new LinkedHashMap<>();
    Map<ModuleCoordinate, Set<ModuleCoordinate>> dependencies = new LinkedHashMap<>();
    Map<ModuleCoordinate, ModuleDescriptor> descriptors =
        graph.stream()
            .map(ResolvedModule::descriptor)
            .collect(
                java.util.stream.Collectors.toMap(
                    ModuleDescriptor::coordinate,
                    java.util.function.Function.identity(),
                    (left, right) -> left,
                    LinkedHashMap::new));
    Set<DocumentId> bindingSources = new LinkedHashSet<>();
    List<ResolvedJarBinding> jarBindings = new java.util.ArrayList<>();
    Map<String, ModuleResource> resources = new LinkedHashMap<>();
    for (ResolvedModule module : graph) {
      dependencies.put(
          module.descriptor().coordinate(), readableDependencies(module.descriptor(), descriptors));
      modulePaths.add(normalize(module.moduleSource().path()));
      bindingSources.addAll(module.bindingSources());
      module.binding().ifPresent(jarBindings::add);
      for (ModuleResource resource : module.resources().values()) {
        if (resources.putIfAbsent(resource.path(), resource) != null) {
          throw new IOException("duplicate module resource " + resource.path());
        }
      }
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
        Optional.of(rootModulePath),
        modulePaths,
        new CompilationScope(coordinates, new ModuleGraph(dependencies)),
        sources,
        exportedSources,
        bindingSources,
        jarBindings,
        resources,
        entryStructure.applicationFactory(),
        entryStructure.mainEntrypoint());
  }

  private ProjectSourceSet loadEmbeddedModule(
      SourceFile entrySource,
      Map<Path, SourceFile> overlays,
      LoadPurpose purpose,
      SourceStructure structure)
      throws IOException {
    SourceFile programSource = structure.programSource();
    Optional<String> packageName = SourceHeader.parse(programSource).packageName();
    ModuleDescriptor descriptor =
        resolveDeclaration(
            modules.evaluate(structure.moduleConfiguration().orElseThrow()), packageName);
    requireAvailableModuleName(descriptor);
    if (descriptor.binding().isPresent()) {
      throw new IOException("an application source cannot declare a Java binding");
    }
    String modulePackage = descriptor.name();
    String sourcePackage = packageName.orElse("");
    if (!sourcePackage.equals(modulePackage) && !sourcePackage.startsWith(modulePackage + ".")) {
      throw new IOException(
          "single-file module source must declare package '"
              + modulePackage
              + "' or one of its child packages");
    }
    String sourcePath =
        sourcePackage.replace('.', '/') + "/" + entrySource.path().getFileName().toString();
    Map<String, SourceFile> moduleSources = Map.of(sourcePath, programSource);
    ModuleLoader.LoadedModule loaded =
        new ModuleLoader().load(new MemoryResolver(moduleSources), descriptor);
    Path root = normalize(entrySource.path()).getParent();
    if (root == null) throw new IOException("application source path has no parent");
    ResolvedModule rootModule =
        new ResolvedModule(
            root,
            entrySource,
            descriptor,
            loaded.sources(),
            loaded.exportedSources(),
            Set.of(),
            Optional.empty(),
            collectResources(root));
    List<ResolvedModule> graph = resolveGraph(rootModule, overlays, purpose);
    validatePackageOwnership(graph);
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

  private List<ResolvedModule> resolveGraph(
      ResolvedModule rootModule, Map<Path, SourceFile> overlays, LoadPurpose purpose)
      throws IOException {
    Map<ModuleCoordinate, ResolvedModule> resolved = new LinkedHashMap<>();
    Map<ModuleCoordinate, ResolvedModule> repository = new LinkedHashMap<>();
    repository.put(rootModule.descriptor().coordinate(), rootModule);
    Map<String, ModuleCoordinate> versions = new LinkedHashMap<>();
    Map<ModuleCoordinate, ModuleRepositoryId> repositories = new LinkedHashMap<>();
    LinkedHashSet<ModuleCoordinate> visiting = new LinkedHashSet<>();
    List<ResolvedModule> ordered = new java.util.ArrayList<>();
    resolveDependencies(
        rootModule,
        repositoryRoot(rootModule),
        overlays,
        repository,
        resolved,
        versions,
        repositories,
        visiting,
        ordered,
        purpose);
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
      Map<ModuleCoordinate, ModuleRepositoryId> repositories,
      LinkedHashSet<ModuleCoordinate> visiting,
      List<ResolvedModule> ordered,
      LoadPurpose purpose)
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
      ResolvedModule dependency =
          resolveDependency(repositoryRoot, requirement, overlays, repository, purpose);
      requireAvailableModuleName(dependency.descriptor());
      resolveDependencies(
          dependency,
          repositoryRoot,
          overlays,
          repository,
          resolved,
          versions,
          repositories,
          visiting,
          ordered,
          purpose);
    }
    visiting.remove(coordinate);
    resolved.put(coordinate, module);
    ordered.add(module);
  }

  private ResolvedModule resolveDependency(
      Path repositoryRoot,
      ModuleRequirement requirement,
      Map<Path, SourceFile> overlays,
      Map<ModuleCoordinate, ResolvedModule> repository,
      LoadPurpose purpose)
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
        ResolvedModule archived = resolveArchivedDependency(repositoryRoot, requirement, purpose);
        repository.put(requirement.coordinate(), archived);
        return archived;
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

  private ResolvedModule resolveArchivedDependency(
      Path repositoryRoot, ModuleRequirement requirement, LoadPurpose purpose) throws IOException {
    AnalysisModuleKey analysisKey = new AnalysisModuleKey(normalize(repositoryRoot), requirement);
    if (purpose == LoadPurpose.ANALYSIS) {
      ResolvedModule cached = analysisModules.get(analysisKey);
      if (cached != null) return cached;
    }
    Path archive = packages.resolve(requirement);
    ModuleArchiveReader.ArchivedModule archived = archive(archive);
    ModuleDescriptor descriptor = archived.descriptor();
    if (!descriptor.coordinate().equals(requirement.coordinate())) {
      throw new IOException(
          "Norm module artifact identity does not match "
              + requirement.name()
              + "@"
              + requirement.version());
    }
    Optional<ResolvedJarBinding> binding = Optional.empty();
    Map<String, String> generatedSources = Map.of();
    if (descriptor.binding().isPresent()) {
      if (purpose == LoadPurpose.RUNTIME) {
        ResolvedJarGraph graph = jars.resolve(repositoryRoot, descriptor.binding().orElseThrow());
        ResolvedJarBinding resolved = generateArchivedJarBinding(descriptor, graph);
        Map<String, String> expected = new LinkedHashMap<>();
        for (GeneratedBindingSource source : resolved.generated().sources()) {
          expected.put(source.relativePath(), source.text());
        }
        generatedSources = Map.copyOf(expected);
        binding = Optional.of(resolved);
      } else {
        Map<String, String> expected = new LinkedHashMap<>();
        int bindingExports = descriptor.binding().orElseThrow().api().size();
        for (String exported : descriptor.exports().subList(0, bindingExports)) {
          String path = descriptor.sourcePath(exported);
          String source = archived.sources().get(path);
          if (source == null) throw new IOException("module binding source is absent: " + path);
          expected.put(path, source);
        }
        generatedSources = Map.copyOf(expected);
      }
    }
    for (Map.Entry<String, String> generated : generatedSources.entrySet()) {
      if (!generated.getValue().equals(archived.sources().get(generated.getKey()))) {
        throw new IOException("Norm module generated sources do not match its pinned JAR binding");
      }
    }
    Path virtualRoot =
        normalize(
            repositoryRoot
                .resolve(".norm/modules")
                .resolve(requirement.repository().value())
                .resolve(requirement.name().replace('.', java.io.File.separatorChar))
                .resolve(Integer.toString(requirement.version())));
    Map<String, SourceFile> sources = new LinkedHashMap<>();
    Set<DocumentId> bindingSources = new LinkedHashSet<>();
    for (Map.Entry<String, String> source : archived.sources().entrySet()) {
      SourceFile generated = SourceFile.of(virtualRoot.resolve(source.getKey()), source.getValue());
      sources.put(source.getKey(), generated);
      if (generatedSources.containsKey(source.getKey())) bindingSources.add(generated.id());
    }
    ModuleLoader.LoadedModule loaded =
        new ModuleLoader().load(new MemoryResolver(sources), descriptor);
    ResolvedModule result =
        new ResolvedModule(
            normalize(repositoryRoot.resolve("dependencies")),
            SourceFile.of(archive, ""),
            descriptor,
            loaded.sources(),
            exportedSources(loaded, bindingSources),
            bindingSources,
            binding,
            archived.resources());
    if (purpose != LoadPurpose.ANALYSIS) return result;
    ResolvedModule cached = analysisModules.putIfAbsent(analysisKey, result);
    return cached == null ? result : cached;
  }

  private ModuleArchiveReader.ArchivedModule archive(Path path) throws IOException {
    Path archive = normalize(path);
    ModuleArchiveReader.ArchivedModule cached = archives.get(archive);
    if (cached != null) return cached;
    ModuleArchiveReader.ArchivedModule loaded = new ModuleArchiveReader().read(archive);
    ModuleArchiveReader.ArchivedModule existing = archives.putIfAbsent(archive, loaded);
    return existing == null ? loaded : existing;
  }

  private void requireAvailableModuleName(ModuleDescriptor descriptor) throws IOException {
    if (reservedModuleNames.contains(descriptor.name())) {
      throw new IOException("module name '" + descriptor.name() + "' is reserved");
    }
  }

  private ResolvedModule resolveModule(SourceFile moduleSource, Map<Path, SourceFile> overlays)
      throws IOException {
    ModuleDeclaration declaration = modules.evaluate(moduleSource);
    ModuleDescriptor descriptor =
        resolveDeclaration(
            declaration,
            declaration.name().isPresent()
                ? Optional.empty()
                : ModuleNameInference.infer(moduleSource, overlays));
    Path root = sourceRoot(moduleSource, descriptor);
    Map<String, SourceFile> sources = new LinkedHashMap<>();
    collectSourceFiles(root, moduleSource, overlays)
        .forEach((path, source) -> sources.put(relativePath(root, path), source));
    Optional<ResolvedJarBinding> binding = Optional.empty();
    Set<DocumentId> bindingSources = Set.of();
    if (descriptor.binding().isPresent()) {
      if (!isPinned(descriptor.binding().orElseThrow())) {
        throw new IOException(
            "JAR binding is not pinned; run 'norm resolve' for " + moduleSource.path());
      }
      Path moduleRoot = normalize(moduleSource.path()).getParent();
      ResolvedJarGraph graph = jars.resolve(moduleRoot, descriptor.binding().orElseThrow());
      ResolvedJarBinding resolvedBinding = generateJarBinding(descriptor, graph);
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
        new ModuleLoader().load(new MemoryResolver(sources), descriptor);
    Map<String, ModuleResource> resources =
        collectResources(normalize(moduleSource.path()).getParent());
    return new ResolvedModule(
        normalize(root),
        moduleSource,
        descriptor,
        loaded.sources(),
        exportedSources(loaded, bindingSources),
        bindingSources,
        binding,
        resources);
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
      return sourceRoot(
          moduleSource,
          resolveDeclaration(
              declaration,
              declaration.name().isPresent()
                  ? Optional.empty()
                  : ModuleNameInference.infer(moduleSource, overlaySources(source, overlays))));
    } catch (IOException exception) {
      return normalize(location.module().orElseThrow().path()).getParent();
    }
  }

  public ModuleDescriptor evaluateModule(SourceFile source) throws IOException {
    if (!isModuleSource(source)) {
      throw new IllegalArgumentException("source is not a module configuration");
    }
    ModuleDeclaration declaration = modules.evaluate(source);
    return resolveDeclaration(
        declaration,
        declaration.name().isPresent()
            ? Optional.empty()
            : ModuleNameInference.infer(source, Map.of()));
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
    ModuleDescriptor descriptor = evaluateModule(source);
    if (descriptor.binding().isEmpty())
      throw new IOException("module does not declare a JAR binding");
    Path moduleRoot = normalize(source.path()).getParent();
    if (moduleRoot == null) throw new IOException("module configuration path has no parent");
    ResolvedJarGraph graph = jars.resolve(moduleRoot, descriptor.binding().orElseThrow());
    return generateJarBinding(descriptor, graph);
  }

  ModuleArchiveContents moduleArchiveContents(SourceFile source) throws IOException {
    if (!isModuleSource(source)) {
      throw new IllegalArgumentException("source is not a module configuration");
    }
    ResolvedModule resolved = resolveModule(source, Map.of());
    return new ModuleArchiveContents(
        resolved.descriptor(), resolved.sources(), resolved.binding(), resolved.resources());
  }

  private ModuleDescriptor resolveDeclaration(
      ModuleDeclaration declaration, Optional<String> inferredName) throws IOException {
    String name =
        declaration
            .name()
            .or(() -> inferredName)
            .orElseThrow(
                () -> new IOException("module name cannot be inferred; declare name explicitly"));
    List<ModuleRequirement> dependencies = new java.util.ArrayList<>();
    for (ModuleDependency dependency : declaration.dependencies()) {
      dependencies.add(packages.resolve(dependency));
    }
    return new ModuleDescriptor(
        new ModuleCoordinate(name, declaration.version().orElse(0)),
        declaration.exports(),
        dependencies,
        declaration.binding());
  }

  private static ResolvedJarBinding generateJarBinding(
      ModuleDescriptor descriptor, ResolvedJarGraph graph) throws IOException {
    return generateJarBinding(descriptor, graph, false);
  }

  private static ResolvedJarBinding generateArchivedJarBinding(
      ModuleDescriptor descriptor, ResolvedJarGraph graph) throws IOException {
    return generateJarBinding(descriptor, graph, true);
  }

  private static ResolvedJarBinding generateJarBinding(
      ModuleDescriptor descriptor, ResolvedJarGraph graph, boolean selectedSurfaceOnly)
      throws IOException {
    try {
      List<String> selectedTypes =
          descriptor.binding().orElseThrow().api().stream()
              .map(dev.w0fv1.norm.value.JarBindingType::name)
              .toList();
      JarApiScanner scanner = new JarApiScanner();
      var surface = scanner.scanSurface(graph, selectedTypes);
      var api = selectedSurfaceOnly ? surface : scanner.scan(graph, selectedTypes);
      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generateSurface(
                  descriptor.coordinate(),
                  descriptor.exports().subList(0, descriptor.binding().orElseThrow().api().size()),
                  descriptor.binding().orElseThrow().api(),
                  graph.contentId(),
                  surface);
      return new ResolvedJarBinding(graph, api, generated);
    } catch (IllegalArgumentException exception) {
      throw new IOException(
          "cannot generate JAR binding for "
              + descriptor.name()
              + "@"
              + descriptor.version()
              + ": "
              + exception.getMessage(),
          exception);
    }
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
    archives.clear();
    analysisModules.clear();
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

  static Map<Path, SourceFile> collectSourceFiles(
      Path root, SourceFile moduleSource, Map<Path, SourceFile> overlays) throws IOException {
    Path modulePath = normalize(moduleSource.path());
    Map<Path, SourceFile> sources = new LinkedHashMap<>();
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
        sources.put(path, overlay == null ? SourceFile.read(path) : overlay);
      }
    }
    overlays.entrySet().stream()
        .filter(source -> source.getKey().startsWith(root))
        .filter(source -> isNormSource(source.getKey()))
        .filter(source -> !source.getKey().equals(modulePath))
        .filter(source -> !insideNestedModule(source.getKey(), nestedRoots))
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(Path::toString)))
        .forEach(source -> sources.put(source.getKey(), source.getValue()));
    return sources;
  }

  private static Map<String, ModuleResource> collectResources(Path moduleRoot) throws IOException {
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

  private record AnalysisModuleKey(Path repositoryRoot, ModuleRequirement requirement) {}

  private enum LoadPurpose {
    ANALYSIS,
    RUNTIME
  }

  private record ResolvedModule(
      Path root,
      SourceFile moduleSource,
      ModuleDescriptor descriptor,
      Map<String, SourceFile> sources,
      Set<DocumentId> exportedSources,
      Set<DocumentId> bindingSources,
      Optional<ResolvedJarBinding> binding,
      Map<String, ModuleResource> resources) {
    private ResolvedModule {
      root = normalize(root);
      Objects.requireNonNull(moduleSource, "moduleSource");
      Objects.requireNonNull(descriptor, "descriptor");
      sources = Map.copyOf(sources);
      exportedSources = Set.copyOf(exportedSources);
      bindingSources = Set.copyOf(bindingSources);
      Objects.requireNonNull(binding, "binding");
      resources = Map.copyOf(resources);
    }
  }

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

  private static Set<DocumentId> exportedSources(
      ModuleLoader.LoadedModule loaded, Set<DocumentId> bindingSources) {
    Set<DocumentId> result = new LinkedHashSet<>(loaded.exportedSources());
    result.addAll(bindingSources);
    return Set.copyOf(result);
  }

  private static boolean isPinned(JarBinding binding) {
    return switch (binding.target()) {
      case LocalJarTarget target -> target.integrity().isPresent();
      case MavenJarTarget target -> target.resolution().isPresent();
    };
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
