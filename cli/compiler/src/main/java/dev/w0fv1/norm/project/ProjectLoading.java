package dev.w0fv1.norm.project;

import static dev.w0fv1.norm.project.ProjectPaths.normalize;
import static dev.w0fv1.norm.project.ProjectPaths.repositoryRoot;

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
import dev.w0fv1.norm.value.ModuleRequirement;
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

final class ProjectLoading {
  private final ProjectLoadContext modules;
  private final JarResolver jars;
  private final ProjectModuleSources moduleSources;
  private final ArchivedModuleLoader archivedModules;
  private final ProjectDependencyGraph dependencies;
  private final List<ProvidedModule> providedModules;
  private final ProjectInputTracker inputs;

  ProjectLoading(
      ProjectLoadContext modules,
      NormPackageResolver packages,
      JarResolver jars,
      ArchivedModuleLoader archivedModules,
      Set<String> reservedModuleNames,
      java.util.function.Consumer<String> progress,
      List<ProvidedModule> providedModules) {
    this.modules = modules;
    this.jars = jars;
    this.archivedModules = archivedModules;
    this.providedModules = providedModules;
    this.inputs = modules.inputs();
    this.moduleSources =
        new ProjectModuleSources(modules, packages, jars, progress, inputs, providedModules);
    this.dependencies =
        new ProjectDependencyGraph(
            moduleSources, archivedModules, reservedModuleNames, inputs, providedModules, modules);
    modules.checkpoint();
  }

  public ProjectSourceSet loadForTests(Path entryPath) throws IOException {
    return load(entryPath, ProjectLoadPurpose.TEST);
  }

  public ProjectSourceSet loadForAnalysis(Path entryPath) throws IOException {
    return load(entryPath, ProjectLoadPurpose.ANALYSIS);
  }

  public ProjectSourceSet loadForAnalysis(Path workspace, ModuleRequirement requirement)
      throws IOException {
    Path root = normalize(workspace);
    ResolvedProjectModule module =
        archivedModules.load(root, requirement, ProjectLoadPurpose.ANALYSIS, modules);
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
    inputs.source(entrySource);
    Map<Path, SourceFile> overlaySources = ProjectLocation.overlays(entrySource, overlays);
    Path entry = normalize(entrySource.path());
    ProjectLocation location = ProjectLocation.discover(entry, overlaySources);
    for (Path candidate : location.candidates()) inputs.candidate(candidate);
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

  private ProjectSourceSet sourceSet(
      Path root,
      Path entry,
      Path rootModulePath,
      List<ResolvedProjectModule> graph,
      SourceStructure entryStructure)
      throws IOException {
    var compilation = ProjectCompilationSources.from(graph, providedModules);
    Set<Path> modulePaths = new LinkedHashSet<>();
    Map<ModuleCoordinate, ModuleDescriptor> descriptors =
        graph.stream()
            .map(ResolvedProjectModule::descriptor)
            .collect(
                java.util.stream.Collectors.toMap(
                    ModuleDescriptor::coordinate,
                    java.util.function.Function.identity(),
                    (left, right) -> left,
                    LinkedHashMap::new));
    providedModules.forEach(
        module -> descriptors.put(module.descriptor().coordinate(), module.descriptor()));
    Map<ModuleCoordinate, FileSnapshot> moduleArchives = new LinkedHashMap<>();
    Map<ModuleCoordinate, dev.w0fv1.norm.frontend.CompiledModule> compiledModules =
        new LinkedHashMap<>();
    Map<ModuleCoordinate, ResolvedJarBinding> jarBindings = new LinkedHashMap<>();
    Map<ModuleCoordinate, Map<String, ModuleResource>> resources = new LinkedHashMap<>();
    for (ResolvedProjectModule module : graph) {
      module
          .compiled()
          .ifPresent(compiled -> compiledModules.put(module.descriptor().coordinate(), compiled));
      module
          .archive()
          .ifPresent(archive -> moduleArchives.put(module.descriptor().coordinate(), archive));
      modulePaths.add(normalize(module.moduleSource().path()));
      module
          .binding()
          .ifPresent(binding -> jarBindings.put(module.descriptor().coordinate(), binding));
      resources.put(module.descriptor().coordinate(), module.resources());
    }
    return new ProjectSourceSet(
        root,
        entry,
        Optional.of(rootModulePath),
        modulePaths,
        descriptors,
        moduleArchives,
        compiledModules,
        compilation.scope(),
        compilation.sources(),
        compilation.exports().stream()
            .map(DocumentId::uri)
            .map(Path::of)
            .collect(java.util.stream.Collectors.toSet()),
        compilation.bindings(),
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

  ProjectLoader.ModuleArchiveContents moduleArchiveContents(SourceFile source) throws IOException {
    if (!ModuleSourceFiles.isModuleSource(source)) {
      throw new IllegalArgumentException("source is not a module configuration");
    }
    ResolvedProjectModule resolved = moduleSources.load(source, Map.of());
    var graph = dependencies.resolve(resolved, Map.of(), ProjectLoadPurpose.RUNTIME);
    resolved = graph.getLast();
    return new ProjectLoader.ModuleArchiveContents(
        resolved.descriptor(),
        resolved.sources(),
        resolved.binding(),
        resolved.resources(),
        ProjectCompilationSources.from(graph, providedModules).library(resolved),
        graph.stream().flatMap(module -> module.compiled().stream()).toList());
  }
}
