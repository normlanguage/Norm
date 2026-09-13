package dev.w0fv1.norm.cli.controller;

import java.io.PrintWriter;
import java.util.Locale;
import java.util.function.Consumer;

final class CommandProgress implements Consumer<String> {
  private final String command;
  private final PrintWriter output;
  private final long started = System.nanoTime();

  CommandProgress(String command, PrintWriter output) {
    this.command = command;
    this.output = output;
  }

  @Override
  public void accept(String message) {
    output.printf(
        Locale.ROOT,
        "[%s +%.1fs] %s%n",
        command,
        (System.nanoTime() - started) / 1_000_000_000.0,
        message);
    output.flush();
  }
}
