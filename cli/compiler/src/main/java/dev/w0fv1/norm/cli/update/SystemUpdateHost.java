package dev.w0fv1.norm.cli.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class SystemUpdateHost implements UpdateHost {
  @Override
  public Optional<PackageManager> packageManager() throws IOException {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    PackageManager manager =
        os.contains("win")
            ? PackageManager.SCOOP
            : os.contains("mac") ? PackageManager.BREW : PackageManager.APT;
    try {
      return execute(
                  manager == PackageManager.SCOOP
                      ? List.of(
                          "powershell.exe",
                          "-NoProfile",
                          "-NonInteractive",
                          "-Command",
                          "if (Get-Command scoop -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }")
                      : List.of(manager.command.getFirst(), "--version"),
                  ignored -> {},
                  Duration.ofSeconds(10))
              == 0
          ? Optional.of(manager)
          : Optional.empty();
    } catch (IOException exception) {
      if (Thread.currentThread().isInterrupted()) throw exception;
      return Optional.empty();
    }
  }

  @Override
  public int updatePackage(PackageManager manager, Consumer<String> output) throws IOException {
    List<String> command =
        manager == PackageManager.SCOOP
            ? List.of(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                "& scoop update normlang; exit $LASTEXITCODE")
            : manager.command;
    return execute(command, output, Duration.ofMinutes(15));
  }

  private static int execute(List<String> command, Consumer<String> output, Duration timeout)
      throws IOException {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    var readFailure = new AtomicReference<IOException>();
    Thread reader =
        Thread.ofVirtual()
            .start(
                () -> {
                  try (var lines = process.inputReader()) {
                    for (String line; (line = lines.readLine()) != null; ) output.accept(line);
                  } catch (IOException exception) {
                    readFailure.set(exception);
                  }
                });
    try {
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS))
        throw new IOException("package manager timed out after " + timeout.toSeconds() + "s");
      reader.join(Duration.ofSeconds(2));
      if (readFailure.get() != null) throw readFailure.get();
      return process.exitValue();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("CLI update was interrupted", exception);
    } finally {
      if (process.isAlive()) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
      }
      process.getInputStream().close();
    }
  }

  @Override
  public Path launcherPath() throws IOException {
    if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"))
      throw new IOException("manual CLI installation currently requires the Windows launcher");
    String value = System.getenv("NORM_LAUNCHER_PATH");
    if (value == null || value.isBlank())
      throw new IOException("Norm launcher path is unavailable; run 'norm setup' first");
    Path path = Path.of(value).toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) throw new IOException("Norm launcher does not exist: " + path);
    return path;
  }

  @Override
  public void install(Path staged, Path target) throws IOException {
    String command =
        "Wait-Process -Id "
            + ProcessHandle.current().pid()
            + " -ErrorAction SilentlyContinue; Move-Item -Force -LiteralPath '"
            + staged.toString().replace("'", "''")
            + "' -Destination '"
            + target.toString().replace("'", "''")
            + "'";
    new ProcessBuilder(
            "powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-Command", command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start();
  }
}
