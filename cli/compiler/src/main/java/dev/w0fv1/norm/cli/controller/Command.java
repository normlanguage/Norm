package dev.w0fv1.norm.cli.controller;

import java.io.PrintWriter;
import java.util.List;

interface Command {
  String name();

  String summary();

  default String usage() {
    return "norm " + name();
  }

  default int help(PrintWriter out, PrintWriter err) {
    out.println("Usage: " + usage());
    out.println(summary());
    out.println("  -h, --help    Show help without running the command");
    return 0;
  }

  int execute(List<String> arguments, PrintWriter out, PrintWriter err);
}
