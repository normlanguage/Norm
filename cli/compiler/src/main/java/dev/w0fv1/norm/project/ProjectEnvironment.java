package dev.w0fv1.norm.project;

import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.frontend.CompilationPrelude;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.frontend.LanguageProfile;
import dev.w0fv1.norm.frontend.ModuleBootstrap;
import dev.w0fv1.norm.jvm.JarResolver;
import dev.w0fv1.norm.jvm.NormPackageResolver;
import dev.w0fv1.norm.stdlib.StandardLibrary;
import dev.w0fv1.norm.value.ModuleDescriptor;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class ProjectEnvironment {
  private final ExecutionBackend backend;
  private final LanguageProfile languageProfile;
  private final java.util.Set<String> reservedModuleNames;

  private ProjectEnvironment(
      ExecutionBackend backend,
      LanguageProfile languageProfile,
      java.util.Set<String> reservedModuleNames) {
    this.backend = Objects.requireNonNull(backend, "backend");
    this.languageProfile = Objects.requireNonNull(languageProfile, "languageProfile");
    this.reservedModuleNames = java.util.Set.copyOf(reservedModuleNames);
  }

  public static ProjectEnvironment bootstrap(ExecutionBackend backend) throws IOException {
    Objects.requireNonNull(backend, "backend");
    CompilationPrelude bootstrap = ModuleBootstrap.prelude();
    var kernel = LanguageProfile.withPrelude(bootstrap);
    ModuleDescriptor descriptor;
    try (ModuleEvaluator evaluator = new ModuleEvaluator(kernel, backend)) {
      descriptor = evaluator.evaluate(StandardLibrary.moduleSource());
    }
    StandardLibrary.LoadedModule standardLibrary = StandardLibrary.load(descriptor);
    CompilationPrelude standardLibraryPrelude =
        new CompilationPrelude(
            standardLibrary.sources(), standardLibrary.exportedSources(), standardLibrary.scope());
    CompilationPrelude prelude = bootstrap.merge(standardLibraryPrelude);
    return new ProjectEnvironment(
        backend,
        LanguageProfile.withPrelude(prelude),
        java.util.Set.of(ModuleBootstrap.coordinate().name(), descriptor.name()));
  }

  public CompilerSession compilerSession() {
    return new CompilerSession(languageProfile);
  }

  public ProjectLoader projectLoader() {
    return new ProjectLoader(new ModuleEvaluator(languageProfile, backend), reservedModuleNames);
  }

  ProjectLoader projectLoader(Path jarCache) {
    return new ProjectLoader(
        new ModuleEvaluator(languageProfile, backend),
        reservedModuleNames,
        new NormPackageResolver(jarCache, jarCache.resolve(".norm-packages")),
        new JarResolver(jarCache));
  }

  ProjectLoader projectLoader(Path moduleRepository, Path jarCache) {
    return new ProjectLoader(
        new ModuleEvaluator(languageProfile, backend),
        reservedModuleNames,
        new NormPackageResolver(moduleRepository, jarCache.resolve(".norm-packages")),
        new JarResolver(jarCache));
  }

  public ProjectLauncher launcher() {
    return new ProjectLauncher(projectLoader(), compilerSession(), backend);
  }

  public ProjectLauncher persistentLauncher() throws IOException {
    return new ProjectLauncher(
        projectLoader(), CompilerSession.persistent(languageProfile), backend);
  }
}
