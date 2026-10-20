package dev.w0fv1.norm.pyrun;

import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.Consumer;

public final class PythonEnvironmentManager {
  private static final String COMPLETE = ".pyrun-complete";
  private final PythonRuntimePlatform platform;

  public PythonEnvironmentManager(PythonRuntimePlatform platform) {
    this.platform = platform;
  }

  public Path ensure(
      Path baseInterpreter, Path environment, Path requirements, Consumer<String> progress)
      throws IOException {
    String requirementsHash = Sha256Digest.compute(requirements).value();
    Path marker = environment.resolve(COMPLETE);
    if (valid(environment, marker, requirementsHash)) return executable(environment);
    Files.createDirectories(environment.getParent());
    Path lockPath = environment.resolveSibling(environment.getFileName() + ".lock");
    try (FileChannel channel =
            FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = channel.lock()) {
      if (!lock.isValid()) throw new IOException("cannot lock Python environment");
      if (valid(environment, marker, requirementsHash)) return executable(environment);
      if (Files.exists(environment, LinkOption.NOFOLLOW_LINKS)) deleteTree(environment);
      progress.accept("Creating Python virtual environment");
      run(
          List.of(baseInterpreter.toString(), "-m", "venv", environment.toString()),
          environment.getParent(),
          progress);
      if (Files.size(requirements) > 0) {
        progress.accept("Installing Python requirements");
        run(
            List.of(
                executable(environment).toString(),
                "-m",
                "pip",
                "install",
                "-r",
                requirements.toString()),
            requirements.getParent(),
            progress);
      }
      Files.writeString(marker, requirementsHash + System.lineSeparator());
      return executable(environment);
    }
  }

  private boolean valid(Path environment, Path marker, String requirementsHash) throws IOException {
    if (!Files.isRegularFile(marker) || !Files.isRegularFile(executable(environment))) return false;
    return Files.readString(marker).trim().equals(requirementsHash);
  }

  private Path executable(Path environment) {
    return platform == PythonRuntimePlatform.WINDOWS_X64
        ? environment.resolve("Scripts").resolve("python.exe")
        : environment.resolve("bin").resolve("python");
  }

  private static void run(List<String> command, Path directory, Consumer<String> progress)
      throws IOException {
    Process process =
        new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
    StringBuilder output = new StringBuilder();
    try (var lines = process.inputReader()) {
      lines
          .lines()
          .forEach(
              line -> {
                output.append(line).append(System.lineSeparator());
                progress.accept(line);
              });
    }
    try {
      int exit = process.waitFor();
      if (exit != 0)
        throw new IOException(
            "Python environment command failed with exit code "
                + exit
                + ": "
                + command.getFirst()
                + System.lineSeparator()
                + output);
    } catch (InterruptedException exception) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new IOException("Python environment command was interrupted", exception);
    }
  }

  private static void deleteTree(Path root) throws IOException {
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
        Files.deleteIfExists(path);
    }
  }
}
