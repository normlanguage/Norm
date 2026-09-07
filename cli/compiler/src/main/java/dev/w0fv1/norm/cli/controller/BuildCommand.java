package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.cli.component.ApplicationBuildPlan;
import dev.w0fv1.norm.cli.component.NativeApplicationExecutable;
import dev.w0fv1.norm.cli.component.WindowsApplicationExecutable;
import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.frontend.CompilationInfrastructureException;
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
    ApplicationBuildOptions options;
    try {
      options = ApplicationBuildOptions.parse(arguments);
    } catch (IllegalArgumentException exception) {
      err.println("error[NORM-CLI-0003]: " + exception.getMessage());
      err.println("Usage: norm build [--jvm] [--diagnostics] [file.norm|project-directory]");
      return ExitCode.USAGE_ERROR;
    }
    Path entry;
    try {
      Path requested = Path.of(options.input()).toAbsolutePath().normalize();
      entry = Files.isDirectory(requested) ? requested.resolve("application.norm") : requested;
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
      NativeApplicationExecutable nativeBuild =
          options.target() == ApplicationBuildTarget.NATIVE
              ? new NativeApplicationExecutable()
              : null;
      try (var project = ApplicationRunner.persistent(environment, progress)) {
        try (var compilation =
            project.compileApplication(
                entry, progress, nativeBuild == null ? List.of() : nativeBuild.supportGraphs())) {
          if (!compilation.result().isSuccess()) {
            for (var diagnostic : compilation.result().diagnostics()) {
              err.println(DiagnosticRenderer.render(diagnostic));
            }
            return ExitCode.COMPILATION_ERROR;
          }
          ApplicationBuildPlan plan =
              ApplicationBuildPlan.from(compilation.application().orElseThrow().sourceSet());
          if (options.target() == ApplicationBuildTarget.JVM) {
            progress.accept("Packaging JVM executable: " + plan.output());
            Path bundle = Files.createTempFile("norm-application-", ".zip");
            try {
              new ApplicationBundleWriter()
                  .write(compilation.application().orElseThrow().sourceSet(), bundle);
              new WindowsApplicationExecutable().write(Path.of(launcher), bundle, plan.output());
            } finally {
              Files.deleteIfExists(bundle);
            }
          } else {
            progress.accept("Building native executable: " + plan.output());
            nativeBuild.write(
                compilation.application().orElseThrow(),
                plan.output(),
                progress,
                options.diagnostics());
          }
          progress.accept("Build completed");
          out.println("Built " + plan.output());
        }
      }
      return ExitCode.SUCCESS;
    } catch (IOException | IllegalArgumentException exception) {
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
