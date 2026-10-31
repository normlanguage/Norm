package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.application.ApplicationRunResult;
import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.cli.component.CommandReportWriter;
import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.frontend.CompilationInfrastructureException;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.project.ApplicationEntry;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class RunCommand implements Command {
  private final Path workingDirectory;

  RunCommand() {
    this(Path.of("").toAbsolutePath());
  }

  RunCommand(Path workingDirectory) {
    this.workingDirectory = workingDirectory.toAbsolutePath().normalize();
  }

  @Override
  public String usage() {
    return "norm run [--debug] [file.norm|module-directory] [-- arguments...]";
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
    int separator = arguments.indexOf("--");
    List<String> applicationArguments =
        separator < 0 ? List.of() : List.copyOf(arguments.subList(separator + 1, arguments.size()));
    if (separator >= 0) arguments = new ArrayList<>(arguments.subList(0, separator));
    boolean debug = arguments.remove("--debug");
    if (arguments.size() > 1) {
      err.println(
          "error[NORM-CLI-0003]: 'run' expects at most one source file or module directory");
      err.println("Usage: " + usage());
      return ExitCode.USAGE_ERROR;
    }

    String source = arguments.isEmpty() ? "." : arguments.getFirst();
    Path entry;
    try {
      entry = ApplicationEntry.resolve(workingDirectory.resolve(source));
    } catch (InvalidPathException | IOException exception) {
      err.printf("error[NORM-CLI-0004]: invalid source path '%s'%n", source);
      return ExitCode.INPUT_ERROR;
    }

    ExecutionContext context =
        ExecutionContext.builder()
            .input(System.in)
            .output(out)
            .error(err)
            .environment(System.getenv())
            .arguments(applicationArguments)
            .platform(JdkSystemPlatform.standard())
            .build();
    ApplicationRunResult result;
    String applicationBundle = System.getenv("NORM_APPLICATION_BUNDLE");
    java.util.function.Consumer<String> progress =
        debug && (applicationBundle == null || applicationBundle.isBlank())
            ? new CommandProgress("run", err)
            : message -> {};
    try {
      if (applicationBundle != null && !applicationBundle.isBlank()) {
        Path bundle = Path.of(applicationBundle);
        String executable = System.getenv("NORM_APPLICATION_EXECUTABLE");
        if (executable != null && !executable.isBlank())
          context =
              context.withApplicationDirectory(
                  Path.of(executable).toAbsolutePath().normalize().getParent());
        dev.w0fv1.norm.runtime.PreparedApplication.read(bundle).execute(bundle, context);
        return context.exitCode();
      }
      var cache =
          new dev.w0fv1.norm.application.PreparedApplicationCache(
              Path.of(System.getProperty("user.home"), ".norm", "cache", "applications"));
      result =
          ApplicationRunner.runSource(
              entry,
              context,
              progress,
              message -> {
                err.println(message);
                err.flush();
              },
              cache);
    } catch (IOException exception) {
      err.printf(
          "error[NORM-CLI-0004]: cannot load source file '%s': %s%n",
          source, exception.getMessage());
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
      CommandReportWriter.writeDiagnostics(result.diagnostics(), err);
      return ExitCode.COMPILATION_ERROR;
    }

    return context.exitCode();
  }
}
