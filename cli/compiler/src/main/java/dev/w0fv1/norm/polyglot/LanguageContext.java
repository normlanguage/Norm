package dev.w0fv1.norm.polyglot;

import com.oracle.truffle.api.TruffleLanguage;
import dev.w0fv1.norm.application.ApplicationCompiler;
import dev.w0fv1.norm.application.ApplicationInput;
import dev.w0fv1.norm.application.CompiledApplication;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.project.ProjectLoader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class LanguageContext {
  private final ExecutionContext execution;
  private final ApplicationCompiler compiler;
  private final java.util.Map<ApplicationInput, CompiledApplication> applications =
      new java.util.LinkedHashMap<>();
  private final ProjectLoader projects;
  private final dev.w0fv1.norm.project.ProjectEnvironment projectEnvironment;
  private boolean closed;

  LanguageContext(
      TruffleLanguage.Env environment,
      CompilerSession compiler,
      ProjectLoader projects,
      dev.w0fv1.norm.project.ProjectEnvironment projectEnvironment) {
    this.projectEnvironment =
        java.util.Objects.requireNonNull(projectEnvironment, "projectEnvironment");
    this.compiler = new ApplicationCompiler(compiler, projects.javaBindings());
    this.projects = java.util.Objects.requireNonNull(projects, "projects");
    var builder =
        ExecutionContext.builder()
            .input(environment.in())
            .error(new java.io.PrintWriter(environment.err(), true, StandardCharsets.UTF_8))
            .output(new PrintWriter(environment.out(), true, StandardCharsets.UTF_8))
            .arguments(List.of(environment.getApplicationArguments()))
            .environment(environment.getEnvironment())
            .platform(JdkSystemPlatform.standard());
    var configured = builder.build();
    execution =
        environment.isFileIOAllowed()
            ? configured.withWorkingDirectory(
                java.nio.file.Path.of(environment.getCurrentWorkingDirectory().getPath()))
            : configured;
  }

  ExecutionContext execution() {
    return execution;
  }

  synchronized CompiledApplication application(ApplicationInput input) {
    if (closed) throw new IllegalStateException("language context is closed");
    var existing = applications.get(input);
    if (existing != null) return existing;
    var compilation = compiler.compile(input, java.util.List.of(), message -> {});
    if (!compilation.result().isSuccess()) {
      throw new IllegalArgumentException(
          compilation.result().diagnostics().stream()
              .map(DiagnosticRenderer::render)
              .collect(java.util.stream.Collectors.joining(System.lineSeparator())));
    }
    var application = compilation.application().orElseThrow();
    applications.put(input, application);
    return application;
  }

  ProjectLoader projects() {
    return projects;
  }

  synchronized void close() {
    if (closed) return;
    closed = true;
    RuntimeException failure = null;
    try {
      for (var application : applications.values()) {
        try {
          application.close();
        } catch (RuntimeException exception) {
          if (failure == null) failure = exception;
          else failure.addSuppressed(exception);
        }
      }
    } finally {
      applications.clear();
      try {
        compiler.close();
      } finally {
        try {
          projects.close();
        } finally {
          try {
            projectEnvironment.close();
          } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
          }
        }
      }
    }
    if (failure != null) throw failure;
  }
}
