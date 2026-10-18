package dev.w0fv1.norm.cli.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.w0fv1.norm.platform.jdk.EnvironmentProxySelector;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class CliUpdateManager {
  private static final URI RELEASE =
      URI.create("https://api.github.com/repos/normlanguage/Norm/releases/latest");

  public Result update(PrintWriter out) throws IOException {
    String packageManager = packageManager();
    if (packageManager != null) {
      List<String> command =
          packageManager.equals("scoop")
              ? List.of("scoop", "update", "normlang")
              : packageManager.equals("brew")
                  ? List.of("brew", "upgrade", "norm")
                  : List.of("apt-get", "install", "--only-upgrade", "norm");
      int exit = run(command, out);
      if (exit == 0) return new Result(packageManager, false);
      out.printf(
          "Package manager %s could not update Norm (exit %d); using manual installation.%n",
          packageManager, exit);
    }
    return manual(out);
  }

  public record Result(String method, boolean deferred) {}

  private Result manual(PrintWriter out) throws IOException {
    if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"))
      throw new IOException("manual CLI installation currently requires the Windows launcher");
    JsonObject release = JsonParser.parseString(read(RELEASE)).getAsJsonObject();
    String asset =
        release.getAsJsonArray("assets").asList().stream()
            .map(value -> value.getAsJsonObject())
            .filter(value -> value.get("name").getAsString().equals("norm.exe"))
            .map(value -> value.get("browser_download_url").getAsString())
            .findFirst()
            .orElseThrow(() -> new IOException("latest Norm release has no Windows CLI asset"));
    Path target = launcherPath();
    Path temporary = Files.createTempFile("norm-update-", ".exe");
    try {
      download(URI.create(asset), temporary);
      Path staged = target.resolveSibling(target.getFileName() + ".updating");
      Files.copy(temporary, staged, StandardCopyOption.REPLACE_EXISTING);
      String command =
          "Start-Sleep -Milliseconds 500; Move-Item -Force -LiteralPath '"
              + ps(staged)
              + "' -Destination '"
              + ps(target)
              + "'";
      new ProcessBuilder(
              "powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-Command", command)
          .start();
      out.println(
          "Downloaded the latest Norm CLI and scheduled installation after this process exits.");
      return new Result("manual", true);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static String packageManager() {
    String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
    if (os.contains("win") && executable("scoop")) return "scoop";
    if (os.contains("mac") && executable("brew")) return "brew";
    if (!os.contains("win") && !os.contains("mac") && executable("apt-get")) return "apt-get";
    return null;
  }

  private static int run(List<String> command, PrintWriter out) throws IOException {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    try (var lines = process.inputReader()) {
      lines.lines().forEach(out::println);
    }
    try {
      return process.waitFor();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("CLI update was interrupted", exception);
    }
  }

  private static boolean executable(String command) {
    try {
      return new ProcessBuilder(command, "--version").redirectErrorStream(true).start().waitFor()
          == 0;
    } catch (IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
      return false;
    }
  }

  private static String read(URI uri) throws IOException {
    HttpClient client = HttpClient.newBuilder().proxy(EnvironmentProxySelector.system()).build();
    try {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(uri)
                  .header("Accept", "application/vnd.github+json")
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200)
        throw new IOException("cannot query latest Norm release: HTTP " + response.statusCode());
      return response.body();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("CLI update was interrupted", exception);
    }
  }

  private static void download(URI uri, Path target) throws IOException {
    HttpClient client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(EnvironmentProxySelector.system())
            .build();
    try {
      HttpResponse<Path> response =
          client.send(
              HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofFile(target));
      if (response.statusCode() != 200)
        throw new IOException("cannot download latest Norm CLI: HTTP " + response.statusCode());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("CLI update was interrupted", exception);
    }
  }

  private static Path launcherPath() throws IOException {
    String value = System.getenv("NORM_LAUNCHER_PATH");
    if (value == null || value.isBlank())
      throw new IOException("Norm launcher path is unavailable; run 'norm setup' first");
    Path path = Path.of(value).toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) throw new IOException("Norm launcher does not exist: " + path);
    return path;
  }

  private static String ps(Path path) {
    return path.toString().replace("'", "''");
  }
}
