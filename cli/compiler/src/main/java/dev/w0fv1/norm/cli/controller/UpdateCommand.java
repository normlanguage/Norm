package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.update.CliUpdateManager;
import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.project.ProjectLoader;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

final class UpdateCommand implements Command {
  @FunctionalInterface
  interface CliUpdater {
    void update(PrintWriter out) throws IOException;
  }

  @FunctionalInterface
  interface ProjectUpdater {
    ProjectLoaderResult update(Path module) throws IOException;
  }

  record ProjectLoaderResult(List<ProjectLoader.DependencyUpdate> updates) {
    ProjectLoaderResult {
      updates = List.copyOf(updates);
    }
  }

  private final CliUpdater cliUpdater;
  private final ProjectUpdater projectUpdater;

  UpdateCommand() {
    this(
        out -> new CliUpdateManager().update(out),
        module -> {
          try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
              var loader = environment.projectLoader()) {
            return new ProjectLoaderResult(loader.updateDependencies(module).updates());
          }
        });
  }

  UpdateCommand(CliUpdater cliUpdater, ProjectUpdater projectUpdater) {
    this.cliUpdater = Objects.requireNonNull(cliUpdater, "cliUpdater");
    this.projectUpdater = Objects.requireNonNull(projectUpdater, "projectUpdater");
  }

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
        "Project dependencies are resolved to their latest stable versions and written to the module declaration source atomically.");
    return ExitCode.SUCCESS;
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    Path project = null;
    boolean cliRequested = arguments.contains("--cli");
    boolean projectRequested = arguments.contains("--project");
    for (int index = 0; index < arguments.size(); index++) {
      String argument = arguments.get(index);
      if (argument.equals("--cli")) {
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
    boolean attempted = false;
    boolean failed = false;
    if (!cliRequested || projectRequested) {
      Path module = null;
      try {
        module =
            dev.w0fv1.norm.project.ModuleInput.resolve(project == null ? Path.of(".") : project);
      } catch (IOException exception) {
        if (projectRequested) {
          err.println("error[NORM-CLI-0004]: " + exception.getMessage());
          failed = true;
        }
      }
      if (module != null && Files.isRegularFile(module)) {
        attempted = true;
        try {
          var result = projectUpdater.update(module);
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
        } catch (IOException | IllegalArgumentException exception) {
          err.println(
              "error[NORM-CLI-0004]: cannot update project dependencies: "
                  + exception.getMessage());
          failed = true;
        }
      } else if (projectRequested && !failed) {
        err.println("error[NORM-CLI-0004]: module source does not exist: " + module);
        failed = true;
      }
    }
    if (!projectRequested || cliRequested) {
      try {
        attempted = true;
        cliUpdater.update(out);
      } catch (IOException exception) {
        err.println("error[NORM-CLI-0004]: cannot update Norm CLI: " + exception.getMessage());
        failed = true;
      }
    }
    if (!attempted && !failed) out.println("Norm is already up to date.");
    return failed ? ExitCode.INPUT_ERROR : ExitCode.SUCCESS;
  }

  private static int usageError(PrintWriter err) {
    err.println("error[NORM-CLI-0003]: invalid 'update' arguments");
    err.println("Usage: " + new UpdateCommand().usage());
    return ExitCode.USAGE_ERROR;
  }
}
