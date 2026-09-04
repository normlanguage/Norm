package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

final class RunCommand implements Command {
  @Override
  public String name() {
    return "run";
  }

  @Override
  public String summary() {
    return "Compile and run a Norm source file or application module";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (arguments.size() != 1) {
      err.println(
          "error[NORM-CLI-0003]: 'run' expects exactly one source file or module directory");
      err.println("Usage: norm run <file.norm|module-directory>");
      return ExitCode.USAGE_ERROR;
    }

    Path entry;
    try {
      entry = Path.of(arguments.getFirst());
      if (Files.isDirectory(entry)) entry = entry.resolve("application.norm");
    } catch (InvalidPathException exception) {
      err.printf("error[NORM-CLI-0004]: invalid source path '%s'%n", arguments.getFirst());
      return ExitCode.INPUT_ERROR;
    }

    dev.w0fv1.norm.value.CompilationResult result;
    try {
      NormRuntime backend = new NormRuntime();
      ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
      String applicationBundle = System.getenv("NORM_APPLICATION_BUNDLE");
      try (var launcher =
          applicationBundle == null || applicationBundle.isBlank()
              ? environment.persistentLauncher()
              : environment.bundledLauncher(Path.of(applicationBundle))) {
        result = launcher.run(entry, ExecutionContext.of(out, JdkSystemPlatform.standard()));
      }
    } catch (IOException exception) {
      err.printf(
          "error[NORM-CLI-0004]: cannot load source file '%s': %s%n",
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
    if (!result.isSuccess()) {
      for (var diagnostic : result.diagnostics()) {
        err.println(DiagnosticRenderer.render(diagnostic));
      }
      return ExitCode.COMPILATION_ERROR;
    }

    return ExitCode.SUCCESS;
  }
}
