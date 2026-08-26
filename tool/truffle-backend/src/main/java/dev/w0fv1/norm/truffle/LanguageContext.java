package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.project.ProjectLoader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class LanguageContext {
  private final ExecutionContext execution;
  private final CompilerSession compiler;
  private final ProjectLoader projects;

  LanguageContext(
      TruffleLanguage.Env environment, CompilerSession compiler, ProjectLoader projects) {
    this.compiler = java.util.Objects.requireNonNull(compiler, "compiler");
    this.projects = java.util.Objects.requireNonNull(projects, "projects");
    execution =
        new ExecutionContext(
            new InputStreamReader(environment.in(), StandardCharsets.UTF_8),
            new PrintWriter(environment.out(), true, StandardCharsets.UTF_8),
            List.of(environment.getApplicationArguments()),
            () -> false);
  }

  ExecutionContext execution() {
    return execution;
  }

  CompilerSession compiler() {
    return compiler;
  }

  ProjectLoader projects() {
    return projects;
  }

  void close() {
    compiler.close();
    projects.close();
  }
}
