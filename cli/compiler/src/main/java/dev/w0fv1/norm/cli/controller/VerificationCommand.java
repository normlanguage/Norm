package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.cli.component.CommandReportWriter;
import dev.w0fv1.norm.cli.value.CommandReport;
import dev.w0fv1.norm.cli.value.CommandReport.Status;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.frontend.CompilationInfrastructureException;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.project.ModuleCompilationException;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.InvalidPathException;
import java.util.List;
import java.util.Locale;

final class VerificationCommand implements Command {
  enum Kind {
    CHECK,
    TEST
  }

  private final Kind kind;

  VerificationCommand(Kind kind) {
    this.kind = java.util.Objects.requireNonNull(kind, "kind");
  }

  @Override
  public String name() {
    return kind.name().toLowerCase(Locale.ROOT);
  }

  @Override
  public String summary() {
    return kind == Kind.CHECK
        ? "Analyze a module or source file without running it"
        : "Compile and run Norm tests";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    CommandReportWriter writer = new CommandReportWriter();
    boolean json = VerificationOptions.requestsJson(arguments);
    VerificationOptions options;
    try {
      options = VerificationOptions.parse(arguments, kind == Kind.TEST);
    } catch (InvalidPathException exception) {
      return writer.write(
          CommandReport.failed(name(), Status.INPUT_ERROR, "NORM-CLI-0004", exception.getMessage()),
          json,
          out,
          err);
    } catch (IllegalArgumentException exception) {
      if (!json)
        err.printf(
            "Usage: norm %s <module-directory|file.norm>%s [--format <text|json>]%n",
            name(), kind == Kind.TEST ? " [--filter <package-or-function>]" : "");
      return writer.write(
          CommandReport.failed(
              name(),
              Status.USAGE_ERROR,
              "NORM-CLI-0003",
              "'" + name() + "' expects a module or source file: " + exception.getMessage()),
          json,
          out,
          err);
    }
    CommandReport report;
    try {
      ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
      switch (kind) {
        case CHECK -> {
          try (var projects = environment.projectLoader();
              var compiler = environment.compilerSession()) {
            var sources = projects.loadForAnalysis(options.entry());
            report =
                CommandReport.checked(
                    name(), compiler.snapshot(sources.compilationRequest()).diagnostics());
          }
        }
        case TEST -> {
          try (var launcher = ApplicationRunner.persistent(environment)) {
            var result =
                launcher.test(
                    options.entry(),
                    ExecutionContext.of(options.json() ? err : out, JdkSystemPlatform.standard()),
                    options.filter());
            report =
                result.compilation().isSuccess()
                    ? CommandReport.tested(result.report().orElseThrow())
                    : CommandReport.checked(name(), result.compilation().diagnostics());
          }
        }
        default -> throw new IllegalStateException("unknown verification kind");
      }
    } catch (ModuleCompilationException exception) {
      report = CommandReport.checked(name(), exception.diagnostics());
    } catch (IOException exception) {
      report =
          CommandReport.failed(name(), Status.INPUT_ERROR, "NORM-CLI-0004", exception.getMessage());
    } catch (CompilationInfrastructureException exception) {
      report =
          CommandReport.failed(
              name(), Status.INTERNAL_ERROR, "NORM-CLI-0005", exception.getMessage());
    } catch (NormExecutionException exception) {
      report = CommandReport.runtimeFailure(name(), exception);
    }
    return writer.write(report, options.json(), out, err);
  }
}
