package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.ProgramRunner;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.SourceFile;
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

    SourceFile source;
    try {
      source = SourceFile.read(Path.of(arguments.getFirst()));
    } catch (InvalidPathException exception) {
      err.printf("error[NORM-CLI-0004]: invalid source path '%s'%n", arguments.getFirst());
      return ExitCode.INPUT_ERROR;
    } catch (IOException exception) {
      err.printf(
          "error[NORM-CLI-0004]: cannot read source file '%s': %s%n",
          arguments.getFirst(), exception.getMessage());
      return ExitCode.INPUT_ERROR;
    }

    var result = new Compiler().compile(source);
    if (!result.isSuccess()) {
      for (var diagnostic : result.diagnostics()) {
        err.println(DiagnosticRenderer.render(diagnostic));
      }
      return ExitCode.COMPILATION_ERROR;
    }

    new ProgramRunner().run(result.program().orElseThrow(), out);
    return ExitCode.SUCCESS;
  }
}
