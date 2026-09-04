package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.component.ApplicationBuildPlan;
import dev.w0fv1.norm.cli.component.WindowsApplicationExecutable;
import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.project.ApplicationBundleWriter;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

final class BuildCommand implements Command {
  @Override
  public String name() {
    return "build";
  }

  @Override
  public String summary() {
    return "Build a self-contained application executable";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (arguments.size() > 1) {
      err.println("error[NORM-CLI-0003]: 'build' expects at most one source file or project");
      err.println("Usage: norm build [file.norm|project-directory]");
      return ExitCode.USAGE_ERROR;
    }
    Path entry;
    try {
      Path requested =
          Path.of(arguments.isEmpty() ? "." : arguments.getFirst()).toAbsolutePath().normalize();
      entry = Files.isDirectory(requested) ? requested.resolve("application.norm") : requested;
    } catch (InvalidPathException exception) {
      err.println("error[NORM-CLI-0004]: invalid build path");
      return ExitCode.INPUT_ERROR;
    }
    String launcher = System.getenv("NORM_LAUNCHER_PATH");
    if (launcher == null || launcher.isBlank()) {
      launcher = System.getProperty("norm.launcher.path", "");
    }
    if (launcher.isBlank()) {
      err.println(
          "error[NORM-CLI-0004]: self-contained Norm launcher is unavailable; run build through norm.exe");
      return ExitCode.INPUT_ERROR;
    }
    try {
      NormRuntime backend = new NormRuntime();
      ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
      try (var project = environment.persistentLauncher()) {
        var compilation = project.compileApplication(entry);
        if (!compilation.result().isSuccess()) {
          for (var diagnostic : compilation.result().diagnostics()) {
            err.println(DiagnosticRenderer.render(diagnostic));
          }
          return ExitCode.COMPILATION_ERROR;
        }
        ApplicationBuildPlan plan = ApplicationBuildPlan.from(compilation.sourceSet());
        Path bundle = Files.createTempFile("norm-application-", ".zip");
        try {
          new ApplicationBundleWriter().write(compilation.sourceSet(), bundle);
          new WindowsApplicationExecutable().write(Path.of(launcher), bundle, plan.output());
        } finally {
          Files.deleteIfExists(bundle);
        }
        out.println("Built " + plan.output());
      }
      return ExitCode.SUCCESS;
    } catch (IOException | IllegalArgumentException exception) {
      err.printf("error[NORM-CLI-0004]: cannot build application: %s%n", exception.getMessage());
      return ExitCode.INPUT_ERROR;
    } catch (dev.w0fv1.norm.frontend.CompilationInfrastructureException exception) {
      err.printf(
          "error[NORM-CLI-0005]: compiler storage unavailable: %s%n", exception.getMessage());
      return ExitCode.INTERNAL_ERROR;
    }
  }
}
