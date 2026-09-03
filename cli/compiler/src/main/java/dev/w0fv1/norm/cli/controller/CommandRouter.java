package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.value.ExitCode;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CommandRouter {
  private final Map<String, Command> commands = new LinkedHashMap<>();

  void register(Command command) {
    Objects.requireNonNull(command, "command");
    Command previous = commands.putIfAbsent(command.name(), command);
    if (previous != null) {
      throw new IllegalArgumentException("duplicate command: " + command.name());
    }
  }

  int route(String[] arguments, PrintWriter out, PrintWriter err) {
    Objects.requireNonNull(arguments, "arguments");
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(err, "err");

    if (arguments.length == 0) {
      return commands.get("help").execute(List.of(), out, err);
    }

    String requested =
        arguments.length == 1 && isNormSource(arguments[0]) ? "run" : alias(arguments[0]);
    Command command = commands.get(requested);
    if (command == null) {
      err.printf("error[NORM-CLI-0001]: unknown command '%s'%n", arguments[0]);
      err.println("Run 'norm --help' for usage.");
      return ExitCode.USAGE_ERROR;
    }

    int argumentStart = requested.equals("run") && isNormSource(arguments[0]) ? 0 : 1;
    return command.execute(List.of(arguments).subList(argumentStart, arguments.length), out, err);
  }

  List<Command> commands() {
    return List.copyOf(commands.values());
  }

  private static String alias(String argument) {
    return switch (argument) {
      case "-h", "--help" -> "help";
      case "-V", "--version" -> "version";
      default -> argument;
    };
  }

  private static boolean isNormSource(String argument) {
    return argument.toLowerCase(java.util.Locale.ROOT).endsWith(".norm");
  }
}
