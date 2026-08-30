package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.value.BuildMetadata;
import java.io.PrintWriter;
import java.util.List;

final class VersionCommand implements Command {
  @Override
  public String name() {
    return "version";
  }

  @Override
  public String summary() {
    return "Show the Norm version";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (!arguments.isEmpty()) {
      err.println("error[NORM-CLI-0002]: 'version' does not accept arguments");
      return ExitCode.USAGE_ERROR;
    }
    out.printf("norm %s%n", BuildMetadata.VERSION);
    return ExitCode.SUCCESS;
  }
}
