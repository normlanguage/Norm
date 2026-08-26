package dev.w0fv1.norm.project;

import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
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
    return compiler.compile(sourceSet.applicationCompilationRequest(entry));
  }

  public CompilationResult run(Path entry, ExecutionContext context) throws IOException {
    CompilationResult result = compile(entry);
    if (result.isSuccess()) {
      backend.execute(
          result.program().orElseThrow().compilation().artifact(),
          Objects.requireNonNull(context, "context"));
    }
    return result;
  }

  @Override
  public void close() {
    compiler.close();
    projects.close();
  }
}
