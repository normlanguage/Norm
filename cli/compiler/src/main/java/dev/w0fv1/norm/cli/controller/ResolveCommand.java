package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.project.ModuleBindingResolutionService;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

final class ResolveCommand implements Command {
  @Override
  public String name() {
    return "resolve";
  }

  @Override
  public String summary() {
    return "Resolve and pin a module JAR binding";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (arguments.size() != 1) {
      err.println("error[NORM-CLI-0003]: 'resolve' expects one module directory or module.norm");
      err.println("Usage: norm resolve <module-directory|module.norm>");
      return ExitCode.USAGE_ERROR;
    }
    Path requested;
    try {
      requested = Path.of(arguments.getFirst()).toAbsolutePath().normalize();
    } catch (InvalidPathException exception) {
      err.printf("error[NORM-CLI-0004]: invalid module path '%s'%n", arguments.getFirst());
      return ExitCode.INPUT_ERROR;
    }
    Path modulePath = Files.isDirectory(requested) ? requested.resolve("module.norm") : requested;
    try {
      ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
      try (var projects = environment.projectLoader()) {
        var resolution = new ModuleBindingResolutionService(projects).resolve(modulePath);
        out.printf(
            "Resolved %s@%d to %s%n",
            resolution.module().name(), resolution.module().version(), resolution.digest());
      }
      return ExitCode.SUCCESS;
    } catch (IOException | IllegalArgumentException exception) {
      err.printf("error[NORM-CLI-0004]: cannot resolve module: %s%n", exception.getMessage());
      return ExitCode.INPUT_ERROR;
    }
  }
}
