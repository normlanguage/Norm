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
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.project.ProjectSourceSet;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public final class CompiledApplication implements AutoCloseable {
  private final ApplicationInput input;
  private final CompilationResult result;
  private final JavaAnnotationProcessingOutput annotationOutput;
  private final JarBindingClasspath.Lease javaClasspath;
  private final dev.w0fv1.norm.execution.RevisionResources.Lease assets;
  private final List<ResolvedJarBinding> javaBindings;
  private boolean closed;
  private boolean transferred;

  public CompiledApplication(
      ApplicationInput input,
      CompilationResult result,
      JavaAnnotationProcessingOutput annotationOutput,
      JarBindingClasspath.Lease javaClasspath,
      TemporaryDirectory workspace,
      List<ResolvedJarBinding> javaBindings) {
    this.input = Objects.requireNonNull(input, "input");
    this.result = Objects.requireNonNull(result, "result");
    if (!result.isSuccess())
      throw new IllegalArgumentException("application compilation must succeed");
    this.annotationOutput = Objects.requireNonNull(annotationOutput, "annotationOutput");
    this.javaClasspath = Objects.requireNonNull(javaClasspath, "javaClasspath");
    assets =
        dev.w0fv1.norm.execution.RevisionResources.own(
            new Assets(javaClasspath, Objects.requireNonNull(workspace, "workspace")));
    this.javaBindings = List.copyOf(javaBindings);
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
    return javaClasspath.classpath();
  }

  public List<ResolvedJarBinding> javaBindings() {
    return javaBindings;
  }

  public JavaApplicationMethodIndex.Analysis methods() {
    return annotationOutput.methods();
  }

  public CoreExecutionPlan executionPlan() {
    return CoreExecutionPlan.forArtifact(
        result.output().orElseThrow().artifact(),
        methods().sourceEntryPoints(result.output().orElseThrow().artifact()));
  }

  public JvmJarBindingRuntime openRuntime() throws IOException {
    if (closed) throw new IllegalStateException("application is closed");
    return new JvmJarBindingRuntime(
        javaBindings, javaClasspath(), List.of(annotationOutput.classes()), assets);
  }

  JvmJarBindingRuntime openRuntime(
      dev.w0fv1.norm.execution.RevisionResources.Lease resources,
      JvmJarBindingRuntime.Realm realm) {
    if (closed) throw new IllegalStateException("application is closed");
    return new JvmJarBindingRuntime(
        javaBindings, javaClasspath(), List.of(annotationOutput.classes()), resources, realm);
  }

  dev.w0fv1.norm.execution.RevisionResources.Lease detachAssets() {
    if (closed || transferred)
      throw new IllegalStateException("application resources are unavailable");
    transferred = true;
    return assets;
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
    closed = true;
    if (!transferred) assets.close();
  }

  private record Assets(JarBindingClasspath.Lease classpath, TemporaryDirectory workspace)
      implements AutoCloseable {
    @Override
    public void close() {
      try (classpath) {
        workspace.close();
      } catch (IOException exception) {
        throw new java.io.UncheckedIOException("Cannot release compilation classpath", exception);
      }
    }
  }
}
