package dev.w0fv1.norm.cli.controller;

import java.io.PrintWriter;
import java.util.List;

interface Command {
  static dev.w0fv1.norm.execution.ExecutionContext.Builder execution(
      PrintWriter out, PrintWriter err) {
    return dev.w0fv1.norm.execution.ExecutionContext.builder()
        .output(out)
        .error(err)
        .environment(System.getenv())
        .platform(dev.w0fv1.norm.platform.jdk.JdkSystemPlatform.standard());
  }

  String name();

  String summary();

  default String usage() {
    return "norm " + name();
  }

  default int help(PrintWriter out, PrintWriter err) {
    out.println("Usage: " + usage());
    out.println(summary());
    out.println("  --diagnostics   Retain structured command diagnostics");
    out.println("  --debug         Show dependency debug messages");
    out.println("  -h, --help    Show help without running the command");
    return 0;
  }

  int execute(List<String> arguments, PrintWriter out, PrintWriter err);
}
