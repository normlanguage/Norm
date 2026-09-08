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
  public int help(PrintWriter out, PrintWriter err) {
    return execute(List.of(), out, err);
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
    out.println("       norm <file.norm>");
    out.println();
    out.println("Commands:");
    for (Command command : router.commands()) {
      out.printf("  %-10s %s%n", command.name(), command.summary());
    }
    out.println();
    out.println("Options:");
    out.println("  -h, --help       Show this help message");
    out.println("  -V, --version    Show the Norm version");
    out.println();
    out.println("Command usage (required: <value>, optional: [value]):");
    for (Command command : router.commands()) {
      if (!command.name().equals("help")) out.println("  " + command.usage());
    }
    out.println();
    out.println(
        """
        Quick start:
          norm check ./app --format json
          norm query ./app --search amount
          norm query ./app app.orders.amount --source --references
          norm refactor name ./app app.orders.amount --to total --preview
          norm test ./app --filter app.orders --format json

        Discovery:
          norm <command> -h        Command arguments, defaults and behavior
          norm refactor name -h    Name refactoring options
        Query and refactor return JSON. Refactoring defaults to preview and does not write files.
        Use qualified names; ambiguous selections return candidates. Quote selectors with parentheses.

        Language and libraries: https://normlanguage.github.io/Norm/tooling/agent
        Norm Skill: https://github.com/normlanguage/Norm-Skill
        """);
    return ExitCode.SUCCESS;
  }
}
