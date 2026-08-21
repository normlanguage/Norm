package dev.w0fv1.norm.cli;

import dev.w0fv1.norm.cli.controller.CliController;
import dev.w0fv1.norm.cli.value.ExitCode;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public final class Main {
  private Main() {}

  public static void main(String[] arguments) {
    var out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
    var err = new PrintWriter(System.err, true, StandardCharsets.UTF_8);
    int exitCode = new CliController().run(arguments, out, err);
    if (exitCode != ExitCode.SUCCESS) {
      System.exit(exitCode);
    }
  }
}
