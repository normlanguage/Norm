package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.value.ExitCode;
import java.io.PrintWriter;
import java.util.List;

final class HelpCommand implements Command {
  private final CommandRouter router;

  HelpCommand(CommandRouter router) {
    this.router = router;
  }

  @Override
  public String name() {
    return "help";
  }

  @Override
  public String summary() {
    return "Show this help message";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (!arguments.isEmpty()) {
      err.println("error[NORM-CLI-0002]: 'help' does not accept arguments");
      return ExitCode.USAGE_ERROR;
    }

    out.println("Usage: norm <command> [options]");
    out.println();
    out.println("Commands:");
    for (Command command : router.commands()) {
      out.printf("  %-10s %s%n", command.name(), command.summary());
    }
    out.println();
    out.println("Options:");
    out.println("  -h, --help       Show this help message");
    out.println("  -V, --version    Show the Norm version");
    return ExitCode.SUCCESS;
  }
}
