package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.project.ProjectTestResult;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

final class TestCommand implements Command {
  @Override
  public String name() {
    return "test";
  }

  @Override
  public String summary() {
    return "Compile and run Norm tests";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (arguments.size() != 1) {
      err.println("error[NORM-CLI-0003]: 'test' expects exactly one source file");
      err.println("Usage: norm test <file.norm>");
      return ExitCode.USAGE_ERROR;
    }

    Path entry;
    try {
      entry = Path.of(arguments.getFirst());
    } catch (InvalidPathException exception) {
      err.printf("error[NORM-CLI-0004]: invalid source path '%s'%n", arguments.getFirst());
      return ExitCode.INPUT_ERROR;
    }

    ProjectTestResult result;
    try {
      NormRuntime backend = new NormRuntime();
      ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
      try (var launcher = environment.persistentLauncher()) {
        result = launcher.test(entry, ExecutionContext.of(out, JdkSystemPlatform.standard()));
      }
    } catch (IOException exception) {
      err.printf(
          "error[NORM-CLI-0004]: cannot load test source '%s': %s%n",
          arguments.getFirst(), exception.getMessage());
      return ExitCode.INPUT_ERROR;
    } catch (dev.w0fv1.norm.frontend.CompilationInfrastructureException exception) {
      err.printf(
          "error[NORM-CLI-0005]: compiler storage unavailable: %s%n", exception.getMessage());
      return ExitCode.INTERNAL_ERROR;
    } catch (NormExecutionException exception) {
      err.printf("error[%s]: %s%n", exception.code().id(), exception.getMessage());
      err.printf(" --> %s:%d:%d%n", exception.uri(), exception.line(), exception.column());
      return ExitCode.RUNTIME_ERROR;
    }
    if (!result.compilation().isSuccess()) {
      for (var diagnostic : result.compilation().diagnostics()) {
        err.println(DiagnosticRenderer.render(diagnostic));
      }
      return ExitCode.COMPILATION_ERROR;
    }

    var report = result.report().orElseThrow();
    report
        .failures()
        .forEach(failure -> err.printf("FAILED %s: %s%n", failure.test(), failure.message()));
    out.printf(
        "Tests: %d found, %d passed, %d failed, %d skipped%n",
        report.testsFound(), report.testsSucceeded(), report.testsFailed(), report.testsSkipped());
    return report.isSuccess() ? ExitCode.SUCCESS : ExitCode.TEST_FAILURE;
  }
}
