package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.component.CommandReportWriter;
import dev.w0fv1.norm.cli.value.CommandReport;
import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.documentation.MarkdownReferenceChecker;
import dev.w0fv1.norm.frontend.CompilationInfrastructureException;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

final class MarkdownCheckCommand implements Command {
  @Override
  public String name() {
    return "docs check";
  }

  @Override
  public String usage() {
    return "norm docs check <markdown-directory> [--module <module-directory>] [--format json]";
  }

  @Override
  public String summary() {
    return "Check versioned declaration references in Markdown";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (arguments.equals(List.of("-h")) || arguments.equals(List.of("--help")))
      return help(out, err);
    if (arguments.isEmpty()) {
      err.println("Usage: " + usage());
      return ExitCode.USAGE_ERROR;
    }
    boolean json = false;
    Optional<Path> module = Optional.empty();
    Path root;
    try {
      root = Path.of(arguments.getFirst());
      for (int index = 1; index < arguments.size(); index++) {
        String option = arguments.get(index);
        if (option.equals("--module") && module.isEmpty() && index + 1 < arguments.size()) {
          module = Optional.of(Path.of(arguments.get(++index)));
        } else if (option.equals("--format")
            && index + 1 < arguments.size()
            && arguments.get(++index).equals("json")) {
          json = true;
        } else {
          err.println("Usage: " + usage());
          return ExitCode.USAGE_ERROR;
        }
      }
    } catch (java.nio.file.InvalidPathException exception) {
      err.println(exception.getMessage());
      return ExitCode.USAGE_ERROR;
    }
    var writer = new CommandReportWriter();
    try {
      var environment = ProjectEnvironment.bootstrap(new NormRuntime());
      try (var projects = environment.projectLoader();
          var compiler = environment.compilerSession()) {
        var result = new MarkdownReferenceChecker(projects, compiler).check(root, module);
        var report = CommandReport.checked(name(), result.diagnostics());
        if (!json)
          out.printf(
              "Checked %d references in %d Markdown files.%n", result.references(), result.files());
        return writer.write(report, json, out, err);
      }
    } catch (IOException exception) {
      return writer.write(
          CommandReport.failed(
              name(), CommandReport.Status.INPUT_ERROR, "NORM-DOC-0001", exception.getMessage()),
          json,
          out,
          err);
    } catch (CompilationInfrastructureException exception) {
      return writer.write(
          CommandReport.failed(
              name(), CommandReport.Status.INTERNAL_ERROR, "NORM-CLI-0005", exception.getMessage()),
          json,
          out,
          err);
    }
  }
}
