package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.component.NativeImageToolchain;
import dev.w0fv1.norm.cli.value.ExitCode;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

final class SetupCommand implements Command {
  @Override
  public String name() {
    return "setup";
  }

  @Override
  public String summary() {
    return "Install the native build toolchain";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (!arguments.isEmpty()) {
      err.println("error[NORM-CLI-0003]: 'setup' does not accept arguments");
      return ExitCode.USAGE_ERROR;
    }
    try {
      NativeImageToolchain toolchain = NativeImageToolchain.ensureAvailable(out::println);
      out.println("Native builds are ready: " + toolchain.executable());
      return ExitCode.SUCCESS;
    } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
      err.println(
          "error[NORM-CLI-0004]: cannot install native build toolchain: " + exception.getMessage());
      return ExitCode.INPUT_ERROR;
    }
  }
}
