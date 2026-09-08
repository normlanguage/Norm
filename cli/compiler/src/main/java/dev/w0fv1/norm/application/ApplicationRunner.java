package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.CompilationResult;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.jvm.JavaAnnotationStub;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.project.ProjectLoader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class ApplicationRunner implements AutoCloseable {
  private final ProjectLoader projects;
  private final ApplicationCompiler compiler;
  private final ExecutionBackend backend;

  public ApplicationRunner(
      ProjectLoader projects, CompilerSession compiler, ExecutionBackend backend) {
    this.projects = Objects.requireNonNull(projects, "projects");
    this.compiler = new ApplicationCompiler(compiler);
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  public static ApplicationRunner open(ProjectEnvironment environment) {
    return new ApplicationRunner(
        environment.projectLoader(), environment.compilerSession(), environment.backend());
  }

  public static ApplicationRunner persistent(ProjectEnvironment environment) throws IOException {
    return persistent(environment, message -> {});
  }

  public static ApplicationRunner persistent(
      ProjectEnvironment environment, Consumer<String> progress) throws IOException {
    return new ApplicationRunner(
        environment.projectLoader(progress),
        environment.persistentCompilerSession(),
        environment.backend());
  }

  public static ApplicationRunner bundled(ProjectEnvironment environment, Path bundle)
      throws IOException {
    return new ApplicationRunner(
        environment.bundledProjectLoader(bundle),
        environment.persistentCompilerSession(),
        environment.backend());
  }

  public ApplicationCompilation compileApplication(Path entry) throws IOException {
    return compileApplication(entry, message -> {}, List.of());
  }

  public ApplicationCompilation compileApplication(
      Path entry, Consumer<String> progress, List<ResolvedJarGraph> supportGraphs)
      throws IOException {
    progress.accept("Resolving sources and dependencies: " + entry);
    var sources = projects.load(entry);
    return compiler.compile(
        new ApplicationInput(sources.applicationCompilationRequest(entry), Optional.of(sources)),
        supportGraphs,
        progress);
  }

  public CompilationResult compile(Path entry) throws IOException {
    try (var compilation = compileApplication(entry)) {
      return compilation.result();
    }
  }

  public CompilationResult run(Path entry, ExecutionContext context) throws IOException {
    try (var compilation = compileApplication(entry)) {
      if (compilation.application().isPresent()) {
        var application = compilation.application().orElseThrow();
        try (var runtime = application.openRuntime()) {
          backend.execute(
              compilation.result().output().orElseThrow().artifact(),
              application.executionPlan(),
              application.context(context).withJarBindingRuntime(runtime));
        }
      }
      return compilation.result();
    }
  }

  public ProjectTestResult test(Path entry, ExecutionContext context) throws IOException {
    return test(entry, context, Optional.empty());
  }

  public ProjectTestResult test(Path entry, ExecutionContext context, Optional<String> filter)
      throws IOException {
    var sources = projects.loadForTests(entry);
    try (var compilation =
        compiler.compile(
            new ApplicationInput(sources.testCompilationRequest(), Optional.of(sources)),
            List.of(),
            message -> {})) {
      if (!compilation.result().isSuccess())
        return new ProjectTestResult(compilation.result(), Optional.empty());
      var application = compilation.application().orElseThrow();
      var report = new AtomicReference<ProjectTestReport>();
      try (var runtime = application.openRuntime()) {
        var execution =
            application
                .context(context)
                .withJarBindingRuntime(runtime)
                .withJavaApplicationEntrypoint(
                    loader ->
                        report.set(
                            new JUnitPlatformTestRunner()
                                .run(
                                    loader,
                                    application.annotations().stubs().stream()
                                        .map(JavaAnnotationStub::binaryName)
                                        .toList(),
                                    new NormTestEngine(application, backend, context, filter))));
        backend.execute(
            compilation.result().output().orElseThrow().artifact(),
            application.executionPlan(),
            execution);
      }
      return new ProjectTestResult(compilation.result(), Optional.of(report.get()));
    }
  }

  @Override
  public void close() {
    try {
      compiler.close();
    } finally {
      projects.close();
    }
  }
}
