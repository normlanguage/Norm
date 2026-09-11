package dev.w0fv1.norm.build;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.application.TemporaryDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public final class ApplicationBuilder {
  private final ApplicationRunner runner;
  private final Optional<Path> launcher;

  public ApplicationBuilder(ApplicationRunner runner, Optional<Path> launcher) {
    this.runner = Objects.requireNonNull(runner, "runner");
    this.launcher =
        Objects.requireNonNull(launcher, "launcher").map(Path::toAbsolutePath).map(Path::normalize);
  }

  public BuildResult build(BuildRequest request, Consumer<BuildProgress> progress)
      throws IOException {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(progress, "progress");
    if (request.target() == ApplicationBuildTarget.JVM
        && (launcher.isEmpty() || !Files.isRegularFile(launcher.orElseThrow()))) {
      throw new IOException("Norm launcher is unavailable");
    }
    Path entry =
        Files.isDirectory(request.input())
            ? request.input().resolve("application.norm")
            : request.input();
    NativeApplicationExecutable nativeBuild =
        request.target() == ApplicationBuildTarget.NATIVE
            ? new NativeApplicationExecutable()
            : null;
    Path output;
    try (var compilation =
        runner.compileApplication(
            entry,
            message -> progress.accept(new BuildProgress(BuildProgress.Stage.COMPILATION, message)),
            nativeBuild == null ? List.of() : nativeBuild.supportGraphs())) {
      if (!compilation.result().isSuccess()) {
        return new BuildResult.CompilationFailure(compilation.result().diagnostics());
      }
      var application = compilation.application().orElseThrow();
      output = ApplicationBuildPlan.from(application.sourceSet()).output();
      switch (request.target()) {
        case JVM -> {
          progress.accept(
              new BuildProgress(
                  BuildProgress.Stage.JVM_PACKAGING, "Packaging JVM executable: " + output));
          try (var staging = new TemporaryDirectory()) {
            Path bundle = staging.path().resolve("application.zip");
            new ApplicationBundleWriter().write(application.sourceSet(), bundle);
            new WindowsApplicationExecutable().write(launcher.orElseThrow(), bundle, output);
          }
        }
        case NATIVE -> {
          progress.accept(
              new BuildProgress(
                  BuildProgress.Stage.NATIVE_BUILD, "Building native executable: " + output));
          var plan = NativeBuildPlanner.plan(application);
          nativeBuild.write(
              plan,
              output,
              message ->
                  progress.accept(new BuildProgress(BuildProgress.Stage.NATIVE_BUILD, message)),
              request.diagnostics());
        }
      }
    }
    progress.accept(new BuildProgress(BuildProgress.Stage.COMPLETE, "Build completed"));
    return new BuildResult.Success(output);
  }
}
