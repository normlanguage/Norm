package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.component.CommandJournal;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

record CommandInvocation(List<String> arguments, boolean debug, boolean diagnostics) {
  private static final ScopedValue<CommandInvocation> CURRENT = ScopedValue.newInstance();
  private static final CommandInvocation DEFAULT = new CommandInvocation(List.of(), false, false);

  CommandInvocation {
    arguments = List.copyOf(arguments);
  }

  static CommandInvocation parse(List<String> arguments, boolean nativeBuild) {
    var remaining = new ArrayList<>(arguments);
    int separator = remaining.indexOf("--");
    var flags = remaining.subList(0, separator < 0 ? remaining.size() : separator);
    boolean debug = flags.remove("--debug");
    boolean diagnostics = flags.contains("--diagnostics");
    if (!nativeBuild) flags.remove("--diagnostics");
    return new CommandInvocation(remaining, debug, diagnostics);
  }

  static CommandInvocation current() {
    return CURRENT.isBound() ? CURRENT.get() : DEFAULT;
  }

  int execute(Command command, PrintWriter out, PrintWriter err) {
    return ScopedValue.where(CURRENT, this)
        .call(
            () ->
                CommandJournal.withDependencyLogs(
                    err,
                    debug,
                    () ->
                        diagnostics
                            ? CommandJournal.capture(
                                command.name(), err, () -> command.execute(arguments, out, err))
                            : command.execute(arguments, out, err)));
  }
}
