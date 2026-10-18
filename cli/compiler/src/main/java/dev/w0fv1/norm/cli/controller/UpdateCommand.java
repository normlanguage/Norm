package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.update.CliUpdateManager;
import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

final class UpdateCommand implements Command {
  @Override
  public String name() {
    return "update";
  }

  @Override
  public String summary() {
    return "Update the CLI and the current module dependencies";
  }

  @Override
  public String usage() {
    return "norm update [--cli] [--project <module-directory>]";
  }

  @Override
  public int help(PrintWriter out, PrintWriter err) {
    Command.super.help(out, err);
    out.println(
        "Updates the CLI through its package manager when available, then downloads and installs it manually when needed.");
    out.println(
        "Project dependencies are resolved to their latest stable versions and written to module.norm atomically.");
    return ExitCode.SUCCESS;
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    Path project = null;
    for (int index = 0; index < arguments.size(); index++) {
      String argument = arguments.get(index);
      if (argument.equals("--cli")) {
        project = null;
        continue;
      }
      if (argument.equals("--project")) {
        if (++index >= arguments.size()) return usageError(err);
        try {
          project = Path.of(arguments.get(index)).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
          err.println("error[NORM-CLI-0004]: invalid project path");
          return ExitCode.INPUT_ERROR;
        }
        continue;
      }
      return usageError(err);
    }
    if (arguments.contains("--cli")) project = null;
    boolean updated = false;
    if (project != null || !arguments.contains("--cli")) {
      Path module =
          project == null
              ? Path.of("module.norm").toAbsolutePath().normalize()
              : (Files.isDirectory(project) ? project.resolve("module.norm") : project);
      if (Files.isRegularFile(module)) {
        try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
            var loader = environment.projectLoader()) {
          var result = loader.updateDependencies(module);
          if (result.updates().isEmpty())
            out.println("Project dependencies are already up to date.");
          for (var update : result.updates()) {
            String previous =
                update.previousVersion() == null
                    ? "latest"
                    : Integer.toString(update.previousVersion());
            out.printf(
                "Updated %s:%s %s -> %d%n",
                update.repository(), update.name(), previous, update.version());
          }
          updated |= !result.updates().isEmpty();
        } catch (IOException | IllegalArgumentException exception) {
          err.println(
              "error[NORM-CLI-0004]: cannot update project dependencies: "
                  + exception.getMessage());
          return ExitCode.INPUT_ERROR;
        }
      } else if (arguments.contains("--project")) {
        err.println("error[NORM-CLI-0004]: project does not contain module.norm: " + module);
        return ExitCode.INPUT_ERROR;
      }
    }
    if (!arguments.contains("--project") || arguments.contains("--cli")) {
      try {
        new CliUpdateManager().update(out);
        updated = true;
      } catch (IOException exception) {
        err.println("error[NORM-CLI-0004]: cannot update Norm CLI: " + exception.getMessage());
        return ExitCode.INPUT_ERROR;
      }
    }
    if (!updated) out.println("Norm is already up to date.");
    return ExitCode.SUCCESS;
  }

  private static int usageError(PrintWriter err) {
    err.println("error[NORM-CLI-0003]: invalid 'update' arguments");
    err.println("Usage: " + new UpdateCommand().usage());
    return ExitCode.USAGE_ERROR;
  }
}
