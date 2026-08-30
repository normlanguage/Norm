package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.component.LanguageServerLauncher;
import dev.w0fv1.norm.cli.value.ExitCode;
import java.io.PrintWriter;
import java.util.List;

final class LspCommand implements Command {
  @Override
  public String name() {
    return "lsp";
  }

  @Override
  public String summary() {
    return "Start the Norm language server over stdio";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (!arguments.isEmpty()) {
      err.println("error[NORM-CLI-0005]: 'lsp' does not accept arguments");
      return ExitCode.USAGE_ERROR;
    }
    try {
      LanguageServerLauncher.run(System.in, System.out);
      return ExitCode.SUCCESS;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      err.println("error[NORM-CLI-0006]: language server was interrupted");
      return ExitCode.INTERNAL_ERROR;
    } catch (RuntimeException exception) {
      Throwable cause = exception;
      while (cause.getCause() != null) {
        cause = cause.getCause();
      }
      err.println(
          "error[NORM-CLI-0006]: language server failed: "
              + cause.getClass().getName()
              + ": "
              + cause.getMessage());
      return ExitCode.INTERNAL_ERROR;
    }
  }
}
