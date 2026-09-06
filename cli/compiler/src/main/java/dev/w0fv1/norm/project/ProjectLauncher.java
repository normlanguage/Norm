package dev.w0fv1.norm.project;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.frontend.SourceHeader;
import dev.w0fv1.norm.jvm.ClasspathResourceMaterializer;
import dev.w0fv1.norm.jvm.JavaAnnotationProcessingOutput;
import dev.w0fv1.norm.jvm.JavaAnnotationProcessorPipeline;
import dev.w0fv1.norm.jvm.JavaApplicationTypeName;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceSpan;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class ProjectLauncher implements AutoCloseable {
  private static final DiagnosticCode JAVA_ANNOTATION_PROCESSING =
      new DiagnosticCode("NORM-JVM-0001");
  private final ProjectLoader projects;
  private final CompilerSession compiler;
  private final ExecutionBackend backend;
  private final JavaAnnotationProcessorPipeline annotationProcessors;
  private final ClasspathResourceMaterializer resources;

  ProjectLauncher(ProjectLoader projects, CompilerSession compiler, ExecutionBackend backend) {
    this.projects = Objects.requireNonNull(projects, "projects");
    this.compiler = Objects.requireNonNull(compiler, "compiler");
    this.backend = Objects.requireNonNull(backend, "backend");
    this.annotationProcessors = new JavaAnnotationProcessorPipeline();
    this.resources = new ClasspathResourceMaterializer();
  }

  public CompilationResult compile(Path entry) throws IOException {
    return compileApplication(entry).result();
  }

  public ApplicationCompilation compileApplication(Path entry) throws IOException {
    return compileApplication(entry, message -> {});
  }

  public ApplicationCompilation compileApplication(
      Path entry, java.util.function.Consumer<String> progress) throws IOException {
    return compileApplication(entry, progress, java.util.List.of());
  }

  public ApplicationCompilation compileApplication(
      Path entry,
      java.util.function.Consumer<String> progress,
      java.util.List<dev.w0fv1.norm.jvm.ResolvedJarGraph> supportGraphs)
      throws IOException {
    Objects.requireNonNull(progress, "progress");
    progress.accept("Resolving sources and dependencies: " + entry);
    ProjectSourceSet sourceSet = projects.load(entry);
    progress.accept(
        "Resolved "
            + sourceSet.sources().size()
            + " sources, "
            + sourceSet.moduleDescriptors().size()
            + " modules, "
            + sourceSet.jarBindings().size()
            + " Java bindings");
    var javaClasspath =
        dev.w0fv1.norm.jvm.JarBindingClasspath.prepare(sourceSet.jarBindings(), supportGraphs);
    PreparedCompilation compilation =
        compile(sourceSet.applicationCompilationRequest(entry), sourceSet, progress, javaClasspath);
    return new ApplicationCompilation(
        sourceSet, compilation.result(), compilation.annotationOutput(), javaClasspath);
  }

  public CompilationResult run(Path entry, ExecutionContext context) throws IOException {
    ProjectSourceSet sourceSet = projects.load(entry);
    PreparedCompilation compilation =
        compile(sourceSet.applicationCompilationRequest(entry), sourceSet);
    CompilationResult result = compilation.result();
    if (result.isSuccess()) {
      try (JvmJarBindingRuntime runtime =
          new JvmJarBindingRuntime(
              sourceSet.jarBindings(),
              java.util.List.of(compilation.annotationOutput().orElseThrow().classes()))) {
        var artifact = result.program().orElseThrow().compilation().artifact();
        var output = compilation.annotationOutput().orElseThrow();
        var entries =
            dev.w0fv1.norm.jvm.JavaApplicationMethodIndex.analyze(output.classes(), output.stubs())
                .entryPoints();
        backend.execute(
            artifact,
            dev.w0fv1.norm.core.CoreExecutionPlan.forArtifact(artifact, entries),
            applicationContext(context, sourceSet).withJarBindingRuntime(runtime));
      }
    }
    return result;
  }

  public ProjectTestResult test(Path entry, ExecutionContext context) throws IOException {
    ProjectSourceSet sourceSet = projects.load(entry);
    PreparedCompilation compilation = compile(sourceSet.testCompilationRequest(), sourceSet);
    if (!compilation.result().isSuccess()) {
      return new ProjectTestResult(compilation.result(), Optional.empty());
    }
    JavaAnnotationProcessingOutput annotationOutput = compilation.annotationOutput().orElseThrow();
    AtomicReference<ProjectTestReport> report = new AtomicReference<>();
    try (JvmJarBindingRuntime runtime =
        new JvmJarBindingRuntime(
            sourceSet.jarBindings(), java.util.List.of(annotationOutput.classes()))) {
      ExecutionContext execution =
          applicationContext(context, sourceSet)
              .withJarBindingRuntime(runtime)
              .withJavaApplicationEntrypoint(
                  loader ->
                      report.set(
                          new JUnitPlatformTestRunner()
                              .run(
                                  loader,
                                  annotationOutput.stubs().stream()
                                      .map(dev.w0fv1.norm.jvm.JavaAnnotationStub::binaryName)
                                      .toList())));
      var artifact = compilation.result().program().orElseThrow().compilation().artifact();
      var entries =
          dev.w0fv1.norm.jvm.JavaApplicationMethodIndex.analyze(
                  annotationOutput.classes(), annotationOutput.stubs())
              .entryPoints();
      backend.execute(
          artifact,
          dev.w0fv1.norm.core.CoreExecutionPlan.forArtifact(artifact, entries),
          execution);
    }
    return new ProjectTestResult(compilation.result(), Optional.of(report.get()));
  }

  private PreparedCompilation compile(CompilationRequest request, ProjectSourceSet sourceSet) {
    return compile(
        request,
        sourceSet,
        message -> {},
        dev.w0fv1.norm.jvm.JarBindingClasspath.prepare(sourceSet.jarBindings()));
  }

  private PreparedCompilation compile(
      CompilationRequest request,
      ProjectSourceSet sourceSet,
      java.util.function.Consumer<String> progress,
      dev.w0fv1.norm.jvm.JarBindingClasspath javaClasspath) {
    progress.accept("Compiling Norm sources");
    CompilationResult result = compiler.compile(request);
    if (!result.isSuccess()) return new PreparedCompilation(result, Optional.empty());
    try {
      progress.accept("Processing Java annotations");
      JavaAnnotationProcessingOutput output =
          annotationProcessors.process(
              result.program().orElseThrow().compilation().artifact(),
              sourceSet.jarBindings(),
              javaClasspath,
              sourceSet.root(),
              request.scope(),
              request.entryDocument(),
              request.bindingSources());
      progress.accept("Materializing " + sourceSet.resources().size() + " application resources");
      resources.materialize(output.classes(), sourceSet.resources());
      return new PreparedCompilation(result, Optional.of(output));
    } catch (IOException exception) {
      var diagnostics = new ArrayList<>(result.diagnostics());
      diagnostics.add(
          Diagnostic.error(
              JAVA_ANNOTATION_PROCESSING,
              exception.getMessage(),
              SourceSpan.at(sourceSet.primarySource(), 0)));
      return new PreparedCompilation(
          new CompilationResult(Optional.empty(), diagnostics), Optional.empty());
    }
  }

  private static ExecutionContext applicationContext(
      ExecutionContext context, ProjectSourceSet sourceSet) {
    String packageName = SourceHeader.parse(sourceSet.primarySource()).packageName().orElse("");
    return Objects.requireNonNull(context, "context")
        .withApplicationPackage(JavaApplicationTypeName.packageName(packageName));
  }

  private record PreparedCompilation(
      CompilationResult result, Optional<JavaAnnotationProcessingOutput> annotationOutput) {
    private PreparedCompilation {
      Objects.requireNonNull(result, "result");
      annotationOutput = Objects.requireNonNull(annotationOutput, "annotationOutput");
    }
  }

  @Override
  public void close() {
    compiler.close();
    projects.close();
  }
}
