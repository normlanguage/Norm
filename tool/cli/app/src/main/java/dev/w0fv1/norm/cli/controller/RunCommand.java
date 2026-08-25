package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.frontend.CompilationInfrastructureException;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.frontend.ProjectLoader;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
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
    return "Compile and run a Norm source file";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (arguments.size() != 1) {
      err.println("error[NORM-CLI-0003]: 'run' expects exactly one source file");
      err.println("Usage: norm run <file.norm>");
      return ExitCode.USAGE_ERROR;
    }

    dev.w0fv1.norm.value.CompilationRequest request;
    try {
      request = new ProjectLoader().load(Path.of(arguments.getFirst())).compilationRequest();
    } catch (InvalidPathException exception) {
      err.printf("error[NORM-CLI-0004]: invalid source path '%s'%n", arguments.getFirst());
      return ExitCode.INPUT_ERROR;
    } catch (IOException exception) {
      err.printf(
          "error[NORM-CLI-0004]: cannot read source file '%s': %s%n",
          arguments.getFirst(), exception.getMessage());
      return ExitCode.INPUT_ERROR;
    }

    dev.w0fv1.norm.value.CompilationResult result;
    try (CompilerSession session = CompilerSession.persistent()) {
      result = session.compile(request);
    } catch (IOException | CompilationInfrastructureException exception) {
      err.printf(
          "error[NORM-CLI-0005]: compiler storage unavailable: %s%n", exception.getMessage());
      return ExitCode.INTERNAL_ERROR;
    }
    if (!result.isSuccess()) {
      for (var diagnostic : result.diagnostics()) {
        err.println(DiagnosticRenderer.render(diagnostic));
      }
      return ExitCode.COMPILATION_ERROR;
    }

    try {
      new NormRuntime().run(result.program().orElseThrow(), out);
      return ExitCode.SUCCESS;
    } catch (NormExecutionException exception) {
      err.printf("error[%s]: %s%n", exception.code().id(), exception.getMessage());
      err.printf(" --> %s:%d:%d%n", exception.uri(), exception.line(), exception.column());
      return ExitCode.RUNTIME_ERROR;
    }
  }
}
