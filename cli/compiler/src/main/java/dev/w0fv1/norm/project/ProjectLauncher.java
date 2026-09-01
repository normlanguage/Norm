package dev.w0fv1.norm.project;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.jvm.JavaAnnotationProcessingException;
import dev.w0fv1.norm.jvm.JavaAnnotationProcessingOutput;
import dev.w0fv1.norm.jvm.JavaAnnotationProcessorPipeline;
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

  ProjectLauncher(ProjectLoader projects, CompilerSession compiler, ExecutionBackend backend) {
    this.projects = Objects.requireNonNull(projects, "projects");
    this.compiler = Objects.requireNonNull(compiler, "compiler");
    this.backend = Objects.requireNonNull(backend, "backend");
    this.annotationProcessors = new JavaAnnotationProcessorPipeline();
  }

  public CompilationResult compile(Path entry) throws IOException {
    ProjectSourceSet sourceSet = projects.load(entry);
    return compile(sourceSet.applicationCompilationRequest(entry), sourceSet).result();
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
        backend.execute(
            result.program().orElseThrow().compilation().artifact(),
            Objects.requireNonNull(context, "context").withJarBindingRuntime(runtime));
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
          Objects.requireNonNull(context, "context")
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
      backend.execute(
          compilation.result().program().orElseThrow().compilation().artifact(), execution);
    }
    return new ProjectTestResult(compilation.result(), Optional.of(report.get()));
  }

  private PreparedCompilation compile(CompilationRequest request, ProjectSourceSet sourceSet) {
    CompilationResult result = compiler.compile(request);
    if (!result.isSuccess()) return new PreparedCompilation(result, Optional.empty());
    try {
      JavaAnnotationProcessingOutput output =
          annotationProcessors.process(
              result.program().orElseThrow().compilation().artifact(),
              sourceSet.jarBindings(),
              sourceSet.root(),
              request.scope(),
              request.entryDocument(),
              request.bindingSources());
      return new PreparedCompilation(result, Optional.of(output));
    } catch (JavaAnnotationProcessingException exception) {
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
