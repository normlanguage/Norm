package dev.w0fv1.norm.cli.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.w0fv1.norm.value.BuildMetadata;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class CliUpdateManager {
  private static final URI RELEASE =
      URI.create("https://api.github.com/repos/normlanguage/Norm/releases/latest");
  private final URI releaseUri;
  private final UpdateHost host;
  private final String currentVersion;

  public CliUpdateManager() {
    this(RELEASE, new SystemUpdateHost(), BuildMetadata.VERSION);
  }

  CliUpdateManager(URI releaseUri, UpdateHost host, String currentVersion) {
    this.releaseUri = releaseUri;
    this.host = host;
    this.currentVersion = currentVersion;
  }

  public Result update(PrintWriter out) throws IOException {
    try (var progress = new UpdateProgress(out);
        var transport = new UpdateTransport(Duration.ofSeconds(30))) {
      progress.stage("Checking Norm updates");
      progress.detail(
          "Current version: "
              + currentVersion
              + (currentVersion.contains("dev") ? " (development build)" : ""));
      progress.stage("Checking the latest Norm release");
      JsonObject release;
      String version;
      URI asset;
      try {
        release = JsonParser.parseString(transport.read(releaseUri)).getAsJsonObject();
        if (!release.has("tag_name")
            || !release.get("tag_name").isJsonPrimitive()
            || !release.get("tag_name").getAsJsonPrimitive().isString()
            || release.get("tag_name").getAsString().isBlank())
          throw new IOException("Norm release information has no version tag");
        version = release.get("tag_name").getAsString();
        progress.message("Latest version: " + version);
        if (atLeast(currentVersion, version)) {
          progress.message("Norm is up to date (" + version + ").");
          return new Result("current", false);
        }

      } catch (com.google.gson.JsonParseException
          | IllegalArgumentException
          | IllegalStateException exception) {
        throw new IOException("invalid Norm release information", exception);
      }
      var manager = host.packageManager();
      if (manager.isPresent()) {
        var selected = manager.orElseThrow();
        progress.stage("Updating Norm via " + selected.label);
        try {
          int exit = host.updatePackage(selected, progress::detail);
          if (exit == 0) {
            String installed = host.installedVersion();
            if (atLeast(installed, version)) {
              progress.message("Verified installed Norm " + installed + ".");
              return new Result(selected.label, false);
            }
            progress.message(
                selected.label
                    + " left Norm at "
                    + installed
                    + "; official version is "
                    + version
                    + ". Using the official release download.");
          } else
            progress.message(
                selected.label
                    + " update failed (exit "
                    + exit
                    + "). Using the official release download.");
        } catch (IOException exception) {
          if (Thread.currentThread().isInterrupted()) throw exception;
          progress.message(
              selected.label
                  + " update failed: "
                  + exception.getMessage()
                  + ". Using the official release download.");
        }
      } else
        progress.detail("No supported package manager found. Using the official release download.");
      try {
        if (!release.has("assets") || !release.get("assets").isJsonArray())
          throw new IOException("Norm release information has no assets");
        asset =
            URI.create(
                release.getAsJsonArray("assets").asList().stream()
                    .filter(value -> value.isJsonObject())
                    .map(value -> value.getAsJsonObject())
                    .filter(value -> value.has("name") && value.has("browser_download_url"))
                    .filter(value -> value.get("name").getAsString().equals("norm.exe"))
                    .map(value -> value.get("browser_download_url").getAsString())
                    .findFirst()
                    .orElseThrow(
                        () -> new IOException("latest Norm release has no Windows CLI asset")));
      } catch (RuntimeException exception) {
        throw new IOException("invalid Norm release assets", exception);
      }
      Path target = host.launcherPath();
      Path staged = Files.createTempFile(target.getParent(), "norm-update-", ".exe");
      boolean scheduled = false;
      try {
        progress.stage("Downloading Norm " + version);
        transport.download(asset, staged, progress);
        progress.message("Downloaded Norm " + version + ".");
        progress.stage("Preparing installation");
        host.install(staged, target);
        scheduled = true;
        progress.message("The launcher will now install the downloaded CLI.");
        progress.message("Run 'norm --version' to verify the installed version.");
        return new Result("manual", true);
      } finally {
        if (!scheduled) Files.deleteIfExists(staged);
      }
    }
  }

  private static boolean atLeast(String installed, String latest) throws IOException {
    String expected = latest.replaceFirst("^v", "");
    if (!expected.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))
      throw new IOException("invalid official Norm version: " + latest);
    String actual = installed.replaceFirst("^v", "");
    if (!actual.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) return false;
    return java.lang.module.ModuleDescriptor.Version.parse(actual)
            .compareTo(java.lang.module.ModuleDescriptor.Version.parse(expected))
        >= 0;
  }

  public record Result(String method, boolean deferred) {}
}
