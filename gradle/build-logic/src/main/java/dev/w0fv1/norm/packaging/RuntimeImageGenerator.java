package dev.w0fv1.norm.packaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class RuntimeImageGenerator {
  private RuntimeImageGenerator() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 2)
      throw new IllegalArgumentException("Expected JDK home and output directory");
    generate(Path.of(arguments[0]), Path.of(arguments[1]));
  }

  public static void generate(Path javaHome, Path output) throws IOException {
    Path bin = javaHome.resolve("bin");
    Path java = executable(bin, "java");
    Path jlink = executable(bin, "jlink");
    String modules =
        Arrays.stream(run(List.of(java.toString(), "--list-modules")).split("\\R"))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> value.split("@", 2)[0])
            .sorted()
            .collect(Collectors.joining(","));
    if (modules.isEmpty()) throw new IOException("No system modules are available in " + javaHome);

    Path destination = output.toAbsolutePath();
    Files.createDirectories(destination.getParent());
    Path staged = Files.createTempDirectory(destination.getParent(), ".norm-runtime-image-");
    try {
      Path image = staged.resolve("runtime");
      run(
          List.of(
              jlink.toString(),
              "--add-modules",
              modules,
              "--strip-debug",
              "--no-header-files",
              "--no-man-pages",
              "--compress",
              "zip-6",
              "--output",
              image.toString()));
      if (Files.exists(destination)) deleteTree(destination);
      Files.move(image, destination);
    } finally {
      if (Files.exists(staged)) deleteTree(staged);
    }
  }

  private static Path executable(Path bin, String name) throws IOException {
    for (String candidate : List.of(name, name + ".exe")) {
      Path path = bin.resolve(candidate);
      if (Files.isRegularFile(path)) return path;
    }
    throw new IOException(name + " is unavailable in " + bin);
  }

  private static String run(List<String> command) throws IOException {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output;
    try {
      output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exitCode = process.waitFor();
      if (exitCode != 0)
        throw new IOException(command.get(0) + " exited with code " + exitCode + ": " + output);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while running " + command.get(0), error);
    }
    return output;
  }

  private static void deleteTree(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
      for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
    }
  }
}
