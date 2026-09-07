package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.CompilationResult;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.SourceHeader;
import dev.w0fv1.norm.jvm.JarBindingClasspath;
import dev.w0fv1.norm.jvm.JavaAnnotationProcessingOutput;
import dev.w0fv1.norm.jvm.JavaApplicationMethodIndex;
import dev.w0fv1.norm.jvm.JavaApplicationTypeName;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.project.ProjectSourceSet;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class CompiledApplication implements AutoCloseable {
  private final ApplicationInput input;
  private final CompilationResult result;
  private final JavaAnnotationProcessingOutput annotationOutput;
  private final JarBindingClasspath javaClasspath;
  private final TemporaryDirectory workspace;
  private boolean closed;

  public CompiledApplication(
      ApplicationInput input,
      CompilationResult result,
      JavaAnnotationProcessingOutput annotationOutput,
      JarBindingClasspath javaClasspath,
      TemporaryDirectory workspace) {
    this.input = Objects.requireNonNull(input, "input");
    this.result = Objects.requireNonNull(result, "result");
    if (!result.isSuccess())
      throw new IllegalArgumentException("application compilation must succeed");
    this.annotationOutput = Objects.requireNonNull(annotationOutput, "annotationOutput");
    this.javaClasspath = Objects.requireNonNull(javaClasspath, "javaClasspath");
    this.workspace = Objects.requireNonNull(workspace, "workspace");
  }

  public ApplicationInput input() {
    return input;
  }

  public ProjectSourceSet sourceSet() {
    return input.project().orElseThrow();
  }

  public CompilationResult result() {
    return result;
  }

  public JavaAnnotationProcessingOutput annotations() {
    return annotationOutput;
  }

  public JarBindingClasspath javaClasspath() {
    return javaClasspath;
  }

  public JavaApplicationMethodIndex.Analysis methods() {
    return annotationOutput.methods();
  }

  public CoreExecutionPlan executionPlan() {
    return CoreExecutionPlan.forArtifact(
        result.output().orElseThrow().artifact(), methods().entryPoints());
  }

  public JvmJarBindingRuntime openRuntime() throws IOException {
    if (closed) throw new IllegalStateException("application is closed");
    return new JvmJarBindingRuntime(
        input.project().map(ProjectSourceSet::jarBindings).orElse(List.of()),
        javaClasspath,
        List.of(annotationOutput.classes()));
  }

  public ExecutionContext context(ExecutionContext context) {
    var source = input.source();
    if (context.applicationDirectory().isEmpty() && "file".equals(source.id().uri().getScheme())) {
      context = context.withApplicationDirectory(source.path().getParent());
    }
    return context.withApplicationPackage(
        JavaApplicationTypeName.packageName(SourceHeader.parse(source).packageName().orElse("")));
  }

  @Override
  public void close() {
    if (closed) return;
    workspace.close();
    closed = true;
  }
}
