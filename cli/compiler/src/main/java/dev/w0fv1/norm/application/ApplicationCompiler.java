package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.CompilationResult;
import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.jvm.JarBindingClasspath;
import dev.w0fv1.norm.jvm.JavaAnnotationProcessorPipeline;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.project.ClasspathResourceMaterializer;
import dev.w0fv1.norm.project.ProjectSourceSet;
import dev.w0fv1.norm.source.SourceSpan;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class ApplicationCompiler implements AutoCloseable {
  private static final DiagnosticCode JAVA_ANNOTATION_PROCESSING =
      new DiagnosticCode("NORM-JVM-0001");
  private final CompilerSession compiler;
  private final JavaAnnotationProcessorPipeline annotations = new JavaAnnotationProcessorPipeline();
  private final ClasspathResourceMaterializer resources = new ClasspathResourceMaterializer();

  public ApplicationCompiler(CompilerSession compiler) {
    this.compiler = Objects.requireNonNull(compiler, "compiler");
  }

  public ApplicationCompilation compile(
      ApplicationInput input, List<ResolvedJarGraph> supportGraphs, Consumer<String> progress) {
    progress.accept("Compiling Norm sources");
    CompilationResult result = compiler.compile(input.request());
    if (!result.isSuccess()) return new ApplicationCompilation(result, Optional.empty());
    var workspace = new TemporaryDirectory();
    boolean transferred = false;
    try {
      var bindings = input.project().map(ProjectSourceSet::jarBindings).orElse(List.of());
      var classpath =
          JarBindingClasspath.prepare(bindings, supportGraphs)
              .materialize(workspace.path().resolve("jars"));
      progress.accept("Processing Java annotations");
      var output =
          annotations.process(
              result.output().orElseThrow().artifact(),
              bindings,
              classpath,
              workspace.path(),
              input.request().scope(),
              input.request().entryDocument(),
              input.request().bindingSources());
      resources.materialize(
          output.classes(), input.project().map(ProjectSourceSet::resources).orElse(Map.of()));
      var application = new CompiledApplication(input, result, output, classpath, workspace);
      transferred = true;
      return new ApplicationCompilation(result, Optional.of(application));
    } catch (IOException exception) {
      var diagnostics = new ArrayList<>(result.diagnostics());
      diagnostics.add(
          Diagnostic.error(
              JAVA_ANNOTATION_PROCESSING,
              exception.getMessage(),
              SourceSpan.at(input.source(), 0)));
      return new ApplicationCompilation(
          new CompilationResult(Optional.empty(), diagnostics), Optional.empty());
    } finally {
      if (!transferred) workspace.close();
    }
  }

  @Override
  public void close() {
    compiler.close();
  }
}
