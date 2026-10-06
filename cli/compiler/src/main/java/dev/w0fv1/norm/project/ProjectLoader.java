package dev.w0fv1.norm.project;

import static dev.w0fv1.norm.project.ProjectPaths.normalize;

import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.jvm.JarResolver;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.packages.NormPackageResolver;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleRequirement;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ProjectLoader implements AutoCloseable {
  private final ModuleEvaluator modules;
  private final JarResolver jars;
  private final NormPackageResolver packages;
  private final ArchivedModuleLoader archivedModules;
  private final List<ProvidedModule> providedModules;
  private final Set<String> reservedModuleNames;
  private final java.util.function.Consumer<String> progress;

  ProjectLoader(
      ModuleEvaluator modules,
      Set<String> reservedModuleNames,
      List<ProvidedModule> providedModules) {
    this(modules, reservedModuleNames, message -> {}, providedModules);
  }

  ProjectLoader(
      ModuleEvaluator modules,
      Set<String> reservedModuleNames,
      java.util.function.Consumer<String> progress,
      List<ProvidedModule> providedModules) {
    this(
        modules,
        reservedModuleNames,
        new NormPackageResolver(defaultCache().resolve("packages")),
        new JarResolver(defaultCache().resolve("maven"), progress),
        progress,
        providedModules);
  }

  ProjectLoader(
      ModuleEvaluator modules,
      Set<String> reservedModuleNames,
      NormPackageResolver packages,
      JarResolver jars,
      List<ProvidedModule> providedModules) {
    this(modules, reservedModuleNames, packages, jars, message -> {}, providedModules);
  }

  private ProjectLoader(
      ModuleEvaluator modules,
      Set<String> reservedModuleNames,
      NormPackageResolver packages,
      JarResolver jars,
      java.util.function.Consumer<String> progress,
      List<ProvidedModule> providedModules) {
    this.modules = Objects.requireNonNull(modules, "modules");
    this.packages = Objects.requireNonNull(packages, "packages");
    this.jars = Objects.requireNonNull(jars, "jars");
    this.providedModules = List.copyOf(providedModules);
    this.reservedModuleNames = Set.copyOf(reservedModuleNames);
    this.progress = progress;
    this.archivedModules = new ArchivedModuleLoader(this.packages, this.jars, progress);
  }

  public List<ResolvedJarBinding> javaBindings() {
    return providedModules.stream().map(ProvidedModule::binding).toList();
  }

  public ProjectLoadResult load(Path entryPath) throws IOException {
    return load(SourceFile.read(normalize(entryPath)), List.of());
  }

  public ProjectLoadResult loadForTests(Path entryPath) throws IOException {
    return capture(
        dev.w0fv1.norm.frontend.CompilationControl.standard(),
        List.of(),
        loading -> loading.loadForTests(entryPath));
  }

  public ProjectLoadResult loadForAnalysis(Path entryPath) throws IOException {
    return capture(
        dev.w0fv1.norm.frontend.CompilationControl.standard(),
        List.of(),
        loading -> loading.loadForAnalysis(entryPath));
  }

  public ProjectLoadResult loadForAnalysis(Path workspace, ModuleRequirement requirement)
      throws IOException {
    return capture(
        dev.w0fv1.norm.frontend.CompilationControl.standard(),
        List.of(),
        loading -> loading.loadForAnalysis(workspace, requirement));
  }

  public ProjectLoadResult load(SourceFile entry, Collection<SourceFile> overlays)
      throws IOException {
    return load(entry, overlays, List.of());
  }

  public ProjectLoadResult load(
      SourceFile entry, Collection<SourceFile> overlays, List<ModuleEvaluation> replayed)
      throws IOException {
    return capture(
        dev.w0fv1.norm.frontend.CompilationControl.standard(),
        replayed,
        loading -> loading.load(entry, overlays));
  }

  public ProjectLoadResult loadForAnalysis(SourceFile entry, Collection<SourceFile> overlays)
      throws IOException {
    return loadForAnalysis(entry, overlays, dev.w0fv1.norm.frontend.CompilationControl.standard());
  }

  public ProjectLoadResult loadForAnalysis(
      SourceFile entry,
      Collection<SourceFile> overlays,
      dev.w0fv1.norm.frontend.CompilationControl control)
      throws IOException {
    return capture(control, List.of(), loading -> loading.loadForAnalysis(entry, overlays));
  }

  public ModuleRequirement resolveReference(
      dev.w0fv1.norm.value.ModuleRepositoryId repository, String path, int version)
      throws IOException {
    return packages.resolveReference(repository, path, version);
  }

  public Path projectRoot(SourceFile source, Collection<SourceFile> overlays) {
    Path path = normalize(source.path());
    try {
      return ProjectLocation.discover(path, ProjectLocation.overlays(source, overlays))
          .standaloneRoot();
    } catch (IOException exception) {
      Path parent = path.getParent();
      if (parent == null)
        throw new IllegalArgumentException("source path has no parent", exception);
      return parent;
    }
  }

  public ModuleDescriptor evaluateModule(SourceFile source) throws IOException {
    return evaluateModule(source, dev.w0fv1.norm.frontend.CompilationControl.standard());
  }

  public ModuleDescriptor evaluateModule(
      SourceFile source, dev.w0fv1.norm.frontend.CompilationControl control) throws IOException {
    var context = new ProjectLoadContext(modules, control, List.of());
    return loading(context).evaluateModule(source);
  }

  public ResolvedJarGraph resolveJarBinding(SourceFile source) throws IOException {
    var context =
        new ProjectLoadContext(
            modules, dev.w0fv1.norm.frontend.CompilationControl.standard(), List.of());
    return loading(context).resolveJarBinding(source);
  }

  public ResolvedJarBinding generateJarBinding(SourceFile source) throws IOException {
    return moduleArchiveContents(source)
        .binding()
        .orElseThrow(() -> new IOException("module does not declare a JAR binding"));
  }

  ModuleArchiveContents moduleArchiveContents(SourceFile source) throws IOException {
    var context =
        new ProjectLoadContext(
            modules, dev.w0fv1.norm.frontend.CompilationControl.standard(), List.of());
    return loading(context).moduleArchiveContents(source);
  }

  public CompilationSnapshot analyzeModule(SourceFile source) {
    return analyzeModule(source, dev.w0fv1.norm.frontend.CompilationControl.standard());
  }

  public CompilationSnapshot analyzeModule(
      SourceFile source, dev.w0fv1.norm.frontend.CompilationControl control) {
    if (!ModuleSourceFiles.isModuleSource(source))
      throw new IllegalArgumentException("source is not a module configuration");
    return modules.snapshot(source, control);
  }

  private ProjectLoading loading(ProjectLoadContext context) {
    return new ProjectLoading(
        context, packages, jars, archivedModules, reservedModuleNames, progress, providedModules);
  }

  private ProjectLoadResult capture(
      dev.w0fv1.norm.frontend.CompilationControl control,
      List<ModuleEvaluation> replayed,
      LoadingOperation operation)
      throws IOException {
    var context = new ProjectLoadContext(modules, control, replayed);
    try {
      return context.finish(operation.load(loading(context)));
    } catch (IOException | IllegalArgumentException exception) {
      if (exception instanceof ProjectLoadException loading)
        throw loading.withInputs(context.inputs().snapshot());
      throw new ProjectLoadException(exception, context.inputs().snapshot());
    }
  }

  @FunctionalInterface
  private interface LoadingOperation {
    ProjectSourceSet load(ProjectLoading loading) throws IOException;
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

  record ModuleArchiveContents(
      ModuleDescriptor descriptor,
      Map<String, SourceFile> sources,
      Optional<ResolvedJarBinding> binding,
      Map<String, ModuleResource> resources,
      dev.w0fv1.norm.value.CompilationRequest compilation,
      List<dev.w0fv1.norm.frontend.CompiledModule> compiledModules) {
    ModuleArchiveContents {
      Objects.requireNonNull(descriptor, "descriptor");
      sources = Map.copyOf(sources);
      Objects.requireNonNull(binding, "binding");
      resources = Map.copyOf(resources);
      Objects.requireNonNull(compilation, "compilation");
      compiledModules = List.copyOf(compiledModules);
    }
  }

  private static Path defaultCache() {
    return Path.of(System.getProperty("user.home"), ".norm", "cache");
  }
}
