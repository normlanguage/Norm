package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.core.CompilationResult;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.frontend.CompilationInfrastructureException;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class RunCommand implements Command {
  @Override
  public String usage() {
    return "norm run [--debug] <file.norm|module-directory>";
  }

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
    arguments = new ArrayList<>(arguments);
    boolean debug = arguments.remove("--debug");
    if (arguments.size() != 1) {
      err.println(
          "error[NORM-CLI-0003]: 'run' expects exactly one source file or module directory");
      err.println("Usage: " + usage());
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

    CompilationResult result;
    String applicationBundle = System.getenv("NORM_APPLICATION_BUNDLE");
    java.util.function.Consumer<String> progress =
        debug && (applicationBundle == null || applicationBundle.isBlank())
            ? new CommandProgress("run", err)
            : message -> {};
    try {
      if (applicationBundle != null && !applicationBundle.isBlank()) {
        Path bundle = Path.of(applicationBundle);
        ExecutionContext context = ExecutionContext.of(out, JdkSystemPlatform.standard());
        String executable = System.getenv("NORM_APPLICATION_EXECUTABLE");
        if (executable != null && !executable.isBlank())
          context =
              context.withApplicationDirectory(
                  Path.of(executable).toAbsolutePath().normalize().getParent());
        dev.w0fv1.norm.runtime.PreparedApplication.read(bundle).execute(bundle, context);
        return ExitCode.SUCCESS;
      }
      progress.accept("Checking prepared application");
      var cache =
          new dev.w0fv1.norm.application.PreparedApplicationCache(
              Path.of(System.getProperty("user.home"), ".norm", "cache", "applications"));
      var prepared = cache.read(entry);
      if (prepared.content().isPresent()) {
        progress.accept("Reused prepared application");
        var content = prepared.content().orElseThrow();
        try (var workspace = cache.prepare(content)) {
          var application = content.application();
          progress.accept("Starting application");
          application.execute(
              workspace.path(),
              ExecutionContext.of(out, JdkSystemPlatform.standard())
                  .withApplicationDirectory(entry.toAbsolutePath().normalize().getParent()));
        }
        return ExitCode.SUCCESS;
      }
      progress.accept("Initializing compiler");
      NormRuntime backend = new NormRuntime();
      ProjectEnvironment environment = ProjectEnvironment.persistent(backend);
      try (var launcher = ApplicationRunner.persistent(environment, progress)) {
        launcher.replayModules(prepared.modules());
        ExecutionContext context = ExecutionContext.of(out, JdkSystemPlatform.standard());
        result = launcher.run(entry, context, progress, cache);
      }
    } catch (IOException exception) {
      err.printf(
          "error[NORM-CLI-0004]: cannot load source file '%s': %s%n",
          arguments.getFirst(), exception.getMessage());
      return ExitCode.INPUT_ERROR;
    } catch (CompilationInfrastructureException exception) {
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
