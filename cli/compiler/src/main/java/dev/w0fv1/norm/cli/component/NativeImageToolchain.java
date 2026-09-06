package dev.w0fv1.norm.cli.component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public record NativeImageToolchain(Path executable) {
  public NativeImageToolchain {
    executable = executable.toAbsolutePath().normalize();
    if (!Files.isRegularFile(executable)) {
      throw new IllegalArgumentException("Native Image executable is unavailable: " + executable);
    }
  }

  public static NativeImageToolchain discover() throws IOException {
    return existing()
        .orElseThrow(
            () ->
                new IOException(
                    "Native Image toolchain is unavailable; run 'norm setup' or set NORM_NATIVE_IMAGE"));
  }

  public static NativeImageToolchain ensureAvailable(Consumer<String> output) throws IOException {
    Optional<NativeImageToolchain> existing = existing();
    if (existing.isPresent()) return existing.get();
    return new NativeImageToolchainInstaller().install(output);
  }

  private static Optional<NativeImageToolchain> existing() {
    List<Path> candidates = new ArrayList<>();
    add(candidates, System.getProperty("norm.native-image.path"));
    add(candidates, System.getenv("NORM_NATIVE_IMAGE"));
    addHome(candidates, System.getProperty("java.home"));
    addHome(candidates, System.getenv("GRAALVM_HOME"));
    String path = System.getenv("PATH");
    if (path != null) {
      for (String directory : path.split(java.io.File.pathSeparator)) {
        if (!directory.isBlank()) addExecutable(candidates, Path.of(directory));
      }
    }
    return candidates.stream()
        .map(Path::toAbsolutePath)
        .map(Path::normalize)
        .filter(Files::isRegularFile)
        .findFirst()
        .map(NativeImageToolchain::new);
  }

  public int run(List<String> arguments, java.util.function.Consumer<String> output)
      throws IOException {
    List<String> command = new ArrayList<>();
    if (System.getProperty("os.name", "").startsWith("Windows")
        && executable.getFileName().toString().endsWith(".cmd")) {
      command.add("cmd.exe");
      command.add("/d");
      command.add("/c");
    }
    command.add(executable.toString());
    command.addAll(arguments);
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    try (var reader =
        new java.io.BufferedReader(
            new java.io.InputStreamReader(
                process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
      for (String line = reader.readLine(); line != null; line = reader.readLine())
        output.accept(line);
    }
    try {
      return process.waitFor();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
      throw new IOException("Native Image build was interrupted", exception);
    }
  }

  private static void add(List<Path> candidates, String value) {
    if (value != null && !value.isBlank()) candidates.add(Path.of(value));
  }

  private static void addHome(List<Path> candidates, String value) {
    if (value == null || value.isBlank()) return;
    addExecutable(candidates, Path.of(value).resolve("bin"));
  }

  private static void addExecutable(List<Path> candidates, Path directory) {
    candidates.add(directory.resolve(windows() ? "native-image.cmd" : "native-image"));
  }

  private static boolean windows() {
    return System.getProperty("os.name", "").startsWith("Windows");
  }
}
