package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.project.ModulePackager;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

final class PackageCommand implements Command {
  @Override
  public String name() {
    return "package";
  }

  @Override
  public String summary() {
    return "Package a resolved module for a Maven repository";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (arguments.size() != 3 || !arguments.get(1).equals("--output")) {
      err.println("error[NORM-CLI-0003]: invalid 'package' arguments");
      err.println("Usage: norm package <module-directory|module.norm> --output <repository>");
      return ExitCode.USAGE_ERROR;
    }
    Path requested;
    Path output;
    try {
      requested = Path.of(arguments.getFirst()).toAbsolutePath().normalize();
      output = Path.of(arguments.getLast()).toAbsolutePath().normalize();
    } catch (InvalidPathException exception) {
      err.println("error[NORM-CLI-0004]: invalid package path");
      return ExitCode.INPUT_ERROR;
    }
    Path modulePath = Files.isDirectory(requested) ? requested.resolve("module.norm") : requested;
    try {
      ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
      try (var projects = environment.projectLoader()) {
        var packaged = new ModulePackager(projects).packageModule(modulePath, output);
        out.println("Packaged " + packaged.archive());
        out.println("Generated " + packaged.pom());
      }
      return ExitCode.SUCCESS;
    } catch (IOException | IllegalArgumentException exception) {
      err.printf("error[NORM-CLI-0004]: cannot package module: %s%n", exception.getMessage());
      return ExitCode.INPUT_ERROR;
    }
  }
}
