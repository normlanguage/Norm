package dev.w0fv1.norm.project;

import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.value.CompilationResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public final class ProjectLauncher implements AutoCloseable {
  private final ProjectLoader projects;
  private final CompilerSession compiler;
  private final ExecutionBackend backend;

  ProjectLauncher(ProjectLoader projects, CompilerSession compiler, ExecutionBackend backend) {
    this.projects = Objects.requireNonNull(projects, "projects");
    this.compiler = Objects.requireNonNull(compiler, "compiler");
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  public CompilationResult compile(Path entry) throws IOException {
    ProjectSourceSet sourceSet = projects.load(entry);
    return compile(entry, sourceSet);
  }

  public CompilationResult run(Path entry, ExecutionContext context) throws IOException {
    ProjectSourceSet sourceSet = projects.load(entry);
    CompilationResult result = compile(entry, sourceSet);
    if (result.isSuccess()) {
      try (JvmJarBindingRuntime runtime = new JvmJarBindingRuntime(sourceSet.jarBindings())) {
        backend.execute(
            result.program().orElseThrow().compilation().artifact(),
            Objects.requireNonNull(context, "context").withJarBindingRuntime(runtime));
      }
    }
    return result;
  }

  private CompilationResult compile(Path entry, ProjectSourceSet sourceSet) throws IOException {
    return compiler.compile(sourceSet.applicationCompilationRequest(entry));
  }

  @Override
  public void close() {
    compiler.close();
    projects.close();
  }
}
