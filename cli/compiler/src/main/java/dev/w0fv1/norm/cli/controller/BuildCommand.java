package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.build.ApplicationBuildTarget;
import dev.w0fv1.norm.build.ApplicationBuilder;
import dev.w0fv1.norm.build.BuildRequest;
import dev.w0fv1.norm.build.BuildResult;
import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.frontend.CompilationInfrastructureException;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

final class BuildCommand implements Command {
  @Override
  public String usage() {
    return "norm build [--jvm] [--diagnostics] [file.norm|project-directory]";
  }

  @Override
  public int help(PrintWriter out, PrintWriter err) {
    Command.super.help(out, err);
    out.println("Default input is the current directory; default target is Native Image.");
    out.println(
        "--jvm selects the JVM development target; --diagnostics retains detailed Native build reports.");
    out.println("--diagnostics cannot be combined with --jvm.");
    return 0;
  }

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
    ApplicationBuildOptions options;
    try {
      options = ApplicationBuildOptions.parse(arguments);
    } catch (IllegalArgumentException exception) {
      err.println("error[NORM-CLI-0003]: " + exception.getMessage());
      err.println("Usage: " + usage());
      return ExitCode.USAGE_ERROR;
    }
    BuildRequest request;
    try {
      request = new BuildRequest(Path.of(options.input()), options.target(), options.diagnostics());
    } catch (InvalidPathException exception) {
      err.println("error[NORM-CLI-0004]: invalid build path");
      return ExitCode.INPUT_ERROR;
    }
    String launcher = launcher();
    if (options.target() == ApplicationBuildTarget.JVM && launcher.isBlank()) {
      err.println(
          "error[NORM-CLI-0004]: self-contained Norm launcher is unavailable; run build through norm.exe");
      return ExitCode.INPUT_ERROR;
    }
    long started = System.nanoTime();
    java.util.function.Consumer<String> progress =
        message -> {
          out.printf(
              java.util.Locale.ROOT,
              "[build +%.1fs] %s%n",
              (System.nanoTime() - started) / 1_000_000_000.0,
              message);
          out.flush();
        };
    try {
      NormRuntime backend = new NormRuntime();
      progress.accept("Target: " + options.target().name().toLowerCase(java.util.Locale.ROOT));
      progress.accept("Initializing compiler");
      ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
      BuildResult result;
      try (var project = ApplicationRunner.persistent(environment, progress)) {
        var builder =
            new ApplicationBuilder(
                project,
                options.target() == ApplicationBuildTarget.JVM
                    ? java.util.Optional.of(Path.of(launcher))
                    : java.util.Optional.empty());
        result = builder.build(request, event -> progress.accept(event.message()));
      }
      return switch (result) {
        case BuildResult.CompilationFailure failure -> {
          for (var diagnostic : failure.diagnostics()) {
            err.println(DiagnosticRenderer.render(diagnostic));
          }
          yield ExitCode.COMPILATION_ERROR;
        }
        case BuildResult.Success success -> {
          out.println("Built " + success.output());
          yield ExitCode.SUCCESS;
        }
      };
    } catch (IOException | java.io.UncheckedIOException | IllegalArgumentException exception) {
      err.printf("error[NORM-CLI-0004]: cannot build application: %s%n", exception);
      var causes =
          java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
      causes.add(exception);
      for (Throwable cause = exception.getCause();
          cause != null && causes.add(cause);
          cause = cause.getCause()) {
        err.printf("  caused by %s%n", cause);
      }
      return ExitCode.INPUT_ERROR;
    } catch (CompilationInfrastructureException exception) {
      err.printf(
          "error[NORM-CLI-0005]: compiler storage unavailable: %s%n", exception.getMessage());
      return ExitCode.INTERNAL_ERROR;
    }
  }

  private static String launcher() {
    String launcher = System.getenv("NORM_LAUNCHER_PATH");
    if (launcher == null || launcher.isBlank()) {
      launcher = System.getProperty("norm.launcher.path", "");
    }
    return launcher;
  }
}
