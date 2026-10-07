package dev.w0fv1.norm.project;

import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.frontend.CompilationPrelude;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.frontend.LanguageProfile;
import dev.w0fv1.norm.frontend.ModuleBootstrap;
import dev.w0fv1.norm.jvm.JarResolver;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.packages.NormPackageResolver;
import dev.w0fv1.norm.stdlib.StandardLibrary;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.JdkModuleTarget;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDeclaration;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleGraph;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ProjectEnvironment implements AutoCloseable {
  private final ExecutionBackend backend;
  private final LanguageProfile languageProfile;
  private final java.util.Set<String> reservedModuleNames;
  private final List<ProvidedModule> providedModules;
  private final dev.w0fv1.norm.jvm.JdkModuleArchive javaMetadata;

  private ProjectEnvironment(
      ExecutionBackend backend,
      LanguageProfile languageProfile,
      java.util.Set<String> reservedModuleNames,
      List<ProvidedModule> providedModules,
      dev.w0fv1.norm.jvm.JdkModuleArchive javaMetadata) {
    this.backend = Objects.requireNonNull(backend, "backend");
    this.languageProfile = Objects.requireNonNull(languageProfile, "languageProfile");
    this.reservedModuleNames = java.util.Set.copyOf(reservedModuleNames);
    this.providedModules = List.copyOf(providedModules);
    this.javaMetadata = Objects.requireNonNull(javaMetadata, "javaMetadata");
  }

  public static ProjectEnvironment bootstrap(ExecutionBackend backend) throws IOException {
    return bootstrap(backend, false);
  }

  public static ProjectEnvironment persistent(ExecutionBackend backend) throws IOException {
    return bootstrap(backend, true);
  }

  private static ProjectEnvironment bootstrap(ExecutionBackend backend, boolean persistent)
      throws IOException {
    Objects.requireNonNull(backend, "backend");
    CompilationPrelude bootstrap = ModuleBootstrap.prelude();
    var kernel = LanguageProfile.withPrelude(bootstrap);
    var descriptors = new java.util.ArrayList<ModuleDescriptor>();
    try (ModuleEvaluator evaluator =
        persistent
            ? ModuleEvaluator.persistent(kernel, backend)
            : new ModuleEvaluator(kernel, backend)) {
      for (String module : List.of("java.base", "std")) {
        ModuleDeclaration declaration =
            evaluator
                .evaluate(
                    StandardLibrary.moduleSource(module),
                    dev.w0fv1.norm.frontend.CompilationControl.standard())
                .declaration();
        descriptors.add(
            new ModuleDescriptor(
                new ModuleCoordinate(
                    declaration.name().orElseThrow(), declaration.version().orElseThrow()),
                declaration.exports(),
                declaration.dependencies().stream()
                    .map(dependency -> dependency.resolved(dependency.version().orElseThrow()))
                    .toList(),
                declaration.binding()));
      }
    }
    ModuleDescriptor javaBaseDescriptor = descriptors.getFirst();
    ResolvedJarBinding javaBinding;
    Path cache = Path.of(System.getProperty("user.home"), ".norm", "cache", "maven");
    var javaMetadata = dev.w0fv1.norm.jvm.JdkModuleArchive.open(cache, "java.base");
    boolean retained = false;
    try {
      var declaredBinding = javaBaseDescriptor.binding().orElseThrow();
      var graph = javaMetadata.graph();
      var target = (JdkModuleTarget) declaredBinding.target();
      javaBaseDescriptor =
          new ModuleDescriptor(
              javaBaseDescriptor.coordinate(),
              javaBaseDescriptor.exports(),
              javaBaseDescriptor.dependencies(),
              Optional.of(
                  new JarBinding(
                      new JdkModuleTarget(target.name(), Optional.of(graph.contentId())),
                      declaredBinding.api())));
      javaBinding = JarBindingPreparer.prepare(javaBaseDescriptor, graph);
      var javaBase = StandardLibrary.load(javaBaseDescriptor, javaBinding.generated().sources());
      ModuleDescriptor descriptor = descriptors.get(1);
      StandardLibrary.LoadedModule standardLibrary = StandardLibrary.load(descriptor);
      var builtinGraph =
          new ModuleGraph(
              Map.of(
                  javaBaseDescriptor.coordinate(), Set.of(),
                  descriptor.coordinate(),
                      descriptor.dependencies().stream()
                          .map(dev.w0fv1.norm.value.ModuleRequirement::coordinate)
                          .collect(java.util.stream.Collectors.toUnmodifiableSet())));
      CompilationPrelude javaBasePrelude =
          new CompilationPrelude(
              javaBase.sources(),
              javaBase.exportedSources(),
              javaBase.scope(),
              javaBase.bindingSources(),
              Map.of(
                  javaBase.sources().getFirst().id(),
                  dev.w0fv1.norm.jvm.JavaScalarConformances.derive(
                      javaBinding.api(),
                      javaBinding.generated().exportedClasses(javaBaseDescriptor.exports()))));
      CompilationPrelude standardLibraryPrelude =
          new CompilationPrelude(
              standardLibrary.sources(),
              standardLibrary.exportedSources(),
              new CompilationScope(standardLibrary.scope().coordinates(), builtinGraph));
      CompilationPrelude prelude = bootstrap.merge(javaBasePrelude).merge(standardLibraryPrelude);
      var result =
          new ProjectEnvironment(
              backend,
              LanguageProfile.withPrelude(prelude),
              java.util.Set.of(
                  ModuleBootstrap.coordinate().name(),
                  descriptor.name(),
                  javaBaseDescriptor.name()),
              List.of(new ProvidedModule(javaBaseDescriptor, javaBinding)),
              javaMetadata);
      retained = true;
      return result;
    } finally {
      if (!retained) javaMetadata.close();
    }
  }

  public ModuleDescriptor javaBaseDescriptor() {
    return providedModules.getFirst().descriptor();
  }

  public List<ResolvedJarBinding> javaBindings() {
    return providedModules.stream().map(ProvidedModule::binding).toList();
  }

  public CompilerSession compilerSession() {
    return new CompilerSession(languageProfile);
  }

  public ProjectLoader projectLoader() {
    return new ProjectLoader(
        new ModuleEvaluator(languageProfile, backend, javaBindings()),
        reservedModuleNames,
        providedModules);
  }

  ProjectLoader projectLoader(Path jarCache) {
    return new ProjectLoader(
        new ModuleEvaluator(languageProfile, backend, javaBindings()),
        reservedModuleNames,
        new NormPackageResolver(jarCache, jarCache.resolve(".norm-packages")),
        new JarResolver(jarCache),
        providedModules);
  }

  ProjectLoader projectLoader(Path moduleRepository, Path jarCache) {
    return projectLoader(
        new NormPackageResolver(moduleRepository, jarCache.resolve(".norm-packages")),
        new JarResolver(jarCache));
  }

  public ProjectLoader projectLoader(NormPackageResolver packages, JarResolver jars) {
    return new ProjectLoader(
        new ModuleEvaluator(languageProfile, backend, javaBindings()),
        reservedModuleNames,
        packages,
        jars,
        providedModules);
  }

  public ExecutionBackend backend() {
    return backend;
  }

  public CompilerSession persistentCompilerSession() throws IOException {
    return CompilerSession.persistent(languageProfile);
  }

  public ProjectLoader persistentProjectLoader(java.util.function.Consumer<String> progress)
      throws IOException {
    return new ProjectLoader(
        ModuleEvaluator.persistent(languageProfile, backend, javaBindings()),
        reservedModuleNames,
        progress,
        providedModules);
  }

  public ProjectLoader bundledProjectLoader(Path bundle) throws IOException {
    Path root = Objects.requireNonNull(bundle, "bundle").toAbsolutePath().normalize();
    return new ProjectLoader(
        new ModuleEvaluator(languageProfile, backend, javaBindings()),
        reservedModuleNames,
        new NormPackageResolver(root.resolve("packages"), root.resolve("cache/packages")),
        JarResolver.bundled(root.resolve("jars")),
        providedModules);
  }

  @Override
  public void close() throws IOException {
    javaMetadata.close();
  }
}
