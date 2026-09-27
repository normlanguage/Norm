package dev.w0fv1.norm.cli.controller;

import dev.w0fv1.norm.cli.value.ExitCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HelloCommand implements Command {
  private static final List<String> FILES =
      List.of(
          "hell.norm",
          "sort.norm",
          "maze.norm",
          "todo.norm",
          "board.norm",
          "README.md",
          "README.zh-CN.md");

  private final Path directory;
  private final TemplateOpener opener;

  HelloCommand() {
    this(Path.of("").toAbsolutePath().normalize(), HelloCommand::openTemplate);
  }

  HelloCommand(Path directory) {
    this(directory, HelloCommand::openTemplate);
  }

  HelloCommand(Path directory, TemplateOpener opener) {
    this.directory = directory;
    this.opener = opener;
  }

  @Override
  public String name() {
    return "hello";
  }

  @Override
  public String summary() {
    return "Create five Norm examples in the current directory";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    if (!arguments.isEmpty()) {
      err.println("error[NORM-CLI-0002]: 'hello' does not accept arguments");
      return ExitCode.USAGE_ERROR;
    }

    Map<String, String> templates = new LinkedHashMap<>();
    try {
      for (String name : FILES) {
        try (InputStream source = HelloCommand.class.getResourceAsStream("/hello/" + name)) {
          if (source == null) {
            err.println("error[NORM-CLI-0004]: missing hello template: " + name);
            return ExitCode.INPUT_ERROR;
          }
          templates.put(name, new String(source.readAllBytes(), StandardCharsets.UTF_8));
        }
      }
      for (String name : FILES) {
        if (Files.exists(directory.resolve(name))) {
          err.println("error[NORM-CLI-0004]: file already exists: " + directory.resolve(name));
          return ExitCode.INPUT_ERROR;
        }
      }
    } catch (IOException failure) {
      err.println("error[NORM-CLI-0004]: cannot load hello templates: " + failure.getMessage());
      return ExitCode.INPUT_ERROR;
    }

    List<Path> created = new ArrayList<>();
    try {
      for (Map.Entry<String, String> template : templates.entrySet()) {
        Path target = directory.resolve(template.getKey());
        try (OutputStream output = opener.open(target)) {
          created.add(target);
          output.write(template.getValue().getBytes(StandardCharsets.UTF_8));
        }
      }
    } catch (IOException failure) {
      for (Path target : created) {
        try {
          Files.deleteIfExists(target);
        } catch (IOException cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
      String detail =
          failure instanceof FileAlreadyExistsException
              ? "file already exists: " + failure.getMessage()
              : "cannot create hello examples: " + failure.getMessage();
      err.println("error[NORM-CLI-0004]: " + detail);
      for (Throwable cleanupFailure : failure.getSuppressed()) {
        err.println("error[NORM-CLI-0004]: cannot roll back: " + cleanupFailure.getMessage());
      }
      return ExitCode.INPUT_ERROR;
    }

    out.println("Created Norm examples:");
    for (String name : FILES) out.println("  " + name);
    out.println("Start with: norm hell.norm");
    return ExitCode.SUCCESS;
  }

  private static OutputStream openTemplate(Path target) throws IOException {
    return Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
  }

  @FunctionalInterface
  interface TemplateOpener {
    OutputStream open(Path target) throws IOException;
  }
}
