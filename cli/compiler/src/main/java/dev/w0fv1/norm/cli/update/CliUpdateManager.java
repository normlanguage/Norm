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
    try (var progress = new UpdateProgress(out, System.console() != null);
        var transport = new UpdateTransport(Duration.ofSeconds(30))) {
      progress.stage("Checking Norm updates");
      progress.detail(
          "Current version: "
              + currentVersion
              + (currentVersion.contains("dev") ? " (development build)" : ""));
      var manager = host.packageManager();
      if (manager.isPresent()) {
        var selected = manager.orElseThrow();
        progress.stage("Updating Norm via " + selected.label);
        try {
          int exit = host.updatePackage(selected, progress::detail);
          if (exit == 0) {
            progress.message(
                selected.label
                    + " update completed. Run 'norm --version' to verify the installed version.");
            return new Result(selected.label, false);
          }
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
      progress.stage("Checking the latest Norm release");
      JsonObject release;
      String version;
      URI asset;
      try {
        release = JsonParser.parseString(transport.read(releaseUri)).getAsJsonObject();
        version = release.get("tag_name").getAsString();
        progress.message("Latest version: " + version);
        if (version.replaceFirst("^v", "").equals(currentVersion.replaceFirst("^v", ""))) {
          progress.message("Norm is up to date (" + version + ").");
          return new Result("current", false);
        }
        asset =
            URI.create(
                release.getAsJsonArray("assets").asList().stream()
                    .map(value -> value.getAsJsonObject())
                    .filter(value -> value.get("name").getAsString().equals("norm.exe"))
                    .map(value -> value.get("browser_download_url").getAsString())
                    .findFirst()
                    .orElseThrow(
                        () -> new IOException("latest Norm release has no Windows CLI asset")));
      } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
        throw new IOException("invalid Norm release information", exception);
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
        progress.message("Installation will continue after this command exits.");
        progress.message("Run 'norm --version' to verify the installed version.");
        return new Result("manual", true);
      } finally {
        if (!scheduled) Files.deleteIfExists(staged);
      }
    }
  }

  public record Result(String method, boolean deferred) {}
}
