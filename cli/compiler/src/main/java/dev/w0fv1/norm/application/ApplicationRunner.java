package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.CompilationResult;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.ExecutionRevision;
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
  public static ApplicationRunResult runSource(
      Path entry,
      ExecutionContext context,
      Consumer<String> progress,
      Consumer<String> diagnostics,
      PreparedApplicationCache cache)
      throws IOException {
    try (var session = openSourceSession(entry, context, progress, diagnostics, cache)) {
      return session.execute();
    }
  }

  public static ApplicationSession openSourceSession(
      Path entry,
      ExecutionContext context,
      Consumer<String> progress,
      Consumer<String> diagnostics,
      PreparedApplicationCache cache)
      throws IOException {
    progress.accept("Checking prepared application");
    var attempt = cache.read(entry);
    var backend = new dev.w0fv1.norm.runtime.NormRuntime();
    var cancellation = new java.util.concurrent.atomic.AtomicBoolean();
    var original = context;
    var updates = new ApplicationUpdateChannel();
    context =
        context
            .withUpdates(updates)
            .withApplicationDirectory(entry.toAbsolutePath().normalize().getParent())
            .withCancellation(() -> cancellation.get() || original.cancellation().getAsBoolean());
    var compiler =
        new SourceApplicationCompiler(entry, context, backend, progress, cache, attempt.modules());
    try {
      ExecutionRevision revision;
      dev.w0fv1.norm.project.ProjectInputSnapshot inputs;
      Optional<CompilationResult> compilation;
      if (attempt.content().isPresent()) {
        progress.accept("Reused prepared application");
        var content = attempt.content().orElseThrow();
        revision =
            ApplicationRuntimeRevision.prepared(
                content, cache.prepare(content), context, compiler.realm());
        inputs = attempt.inputs();
        compilation = Optional.empty();
      } else {
        var candidate = compiler.compile();
        compilation = Optional.of(candidate.result());
        revision = candidate.revision().orElse(null);
        inputs = candidate.inputs();
      }
      return new ApplicationSession(
          backend,
          compiler,
          compilation,
          revision,
          inputs,
          cancellation,
          progress,
          diagnostics,
          updates);
    } catch (IOException | RuntimeException | Error exception) {
      compiler.close();
      throw exception;
    }
  }

  private final ProjectLoader projects;
  private final ApplicationCompiler compiler;
  private final ExecutionBackend backend;

  public ApplicationRunner(
      ProjectLoader projects, CompilerSession compiler, ExecutionBackend backend) {
    this.projects = Objects.requireNonNull(projects, "projects");
    this.compiler = new ApplicationCompiler(compiler, projects.javaBindings());
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
        environment.persistentProjectLoader(progress),
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
    var sources = projects.load(entry).sources();
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
    return run(entry, context, message -> {});
  }

  public CompilationResult run(
      Path entry, ExecutionContext context, Consumer<String> progress, Consumer<String> diagnostics)
      throws IOException {
    return run(entry, context, progress, null, List.of(), diagnostics);
  }

  ApplicationCandidate compileCandidate(
      Path entry,
      ExecutionContext context,
      Consumer<String> progress,
      PreparedApplicationCache cache,
      List<dev.w0fv1.norm.project.ModuleEvaluation> replay,
      dev.w0fv1.norm.jvm.JvmJarBindingRuntime.Realm realm)
      throws IOException {
    progress.accept("Resolving sources and dependencies: " + entry);
    var loaded = projects.load(dev.w0fv1.norm.source.SourceFile.read(entry), List.of(), replay);
    var sources = loaded.sources();
    var compilation =
        compiler.compile(
            new ApplicationInput(
                sources.applicationCompilationRequest(entry), Optional.of(sources)),
            List.of(),
            progress);
    if (!compilation.result().isSuccess()) {
      compilation.close();
      return new ApplicationCandidate(compilation.result(), loaded.inputs(), Optional.empty());
    }
    var application = compilation.application().orElseThrow();
    try {
      if (cache != null)
        cache.write(entry, application, loaded.inputs(), loaded.moduleEvaluations());
      progress.accept("Preparing Java runtime");
      var revision = ApplicationRuntimeRevision.compiled(application, context, realm);
      return new ApplicationCandidate(compilation.result(), loaded.inputs(), Optional.of(revision));
    } catch (IOException | RuntimeException | Error exception) {
      compilation.close();
      throw exception;
    }
  }

  public CompilationResult run(Path entry, ExecutionContext context, Consumer<String> progress)
      throws IOException {
    return run(entry, context, progress, null, List.of());
  }

  public CompilationResult run(
      Path entry,
      ExecutionContext context,
      Consumer<String> progress,
      PreparedApplicationCache cache)
      throws IOException {
    return run(entry, context, progress, cache, List.of());
  }

  public CompilationResult run(
      Path entry,
      ExecutionContext context,
      Consumer<String> progress,
      PreparedApplicationCache cache,
      List<dev.w0fv1.norm.project.ModuleEvaluation> replay)
      throws IOException {
    return run(
        entry,
        context,
        progress,
        cache,
        replay,
        message -> {
          context.error().println(message);
          context.error().flush();
        });
  }

  private CompilationResult run(
      Path entry,
      ExecutionContext context,
      Consumer<String> progress,
      PreparedApplicationCache cache,
      List<dev.w0fv1.norm.project.ModuleEvaluation> replay,
      Consumer<String> diagnostics)
      throws IOException {
    try (var session = openSession(entry, context, progress, cache, replay, diagnostics)) {
      session.execute();
      return session.compilation().orElseThrow();
    }
  }

  public ApplicationSession openSession(
      Path entry, ExecutionContext context, Consumer<String> progress, Consumer<String> diagnostics)
      throws IOException {
    return openSession(entry, context, progress, null, List.of(), diagnostics);
  }

  private ApplicationSession openSession(
      Path entry,
      ExecutionContext context,
      Consumer<String> progress,
      PreparedApplicationCache cache,
      List<dev.w0fv1.norm.project.ModuleEvaluation> replay,
      Consumer<String> diagnostics)
      throws IOException {
    var cancellation = new java.util.concurrent.atomic.AtomicBoolean();
    var original = context;
    var updates = new ApplicationUpdateChannel();
    context =
        context
            .withUpdates(updates)
            .withCancellation(() -> cancellation.get() || original.cancellation().getAsBoolean());
    var candidateCompiler =
        new SourceApplicationCompiler(this, entry, context, progress, cache, replay);
    try {
      var candidate = candidateCompiler.compile();
      return new ApplicationSession(
          backend,
          candidateCompiler,
          Optional.of(candidate.result()),
          candidate.revision().orElse(null),
          candidate.inputs(),
          cancellation,
          progress,
          diagnostics,
          updates);
    } catch (IOException | RuntimeException | Error exception) {
      candidateCompiler.close();
      throw exception;
    }
  }

  public ProjectTestResult test(Path entry, ExecutionContext context) throws IOException {
    return test(entry, context, Optional.empty());
  }

  public ProjectTestResult test(Path entry, ExecutionContext context, Optional<String> filter)
      throws IOException {
    var sources = projects.loadForTests(entry).sources();
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
