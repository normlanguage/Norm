package dev.w0fv1.norm.gradle;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public record ReleaseTarget(String target, String assetTemplate, boolean standalone) {
  public String asset(String version) {
    return assetTemplate.replace("{version}", version);
  }

  public static ReleaseTarget select(Path manifest, String osName, String osArch)
      throws IOException {
    String operatingSystem = osName.toLowerCase(Locale.ROOT);
    String system =
        operatingSystem.startsWith("windows")
            ? "win32"
            : operatingSystem.startsWith("mac")
                ? "darwin"
                : operatingSystem.startsWith("linux") ? "linux" : null;
    String architecture = osArch.toLowerCase(Locale.ROOT);
    String arch =
        architecture.equals("amd64") || architecture.equals("x86_64")
            ? "x64"
            : architecture.equals("aarch64") || architecture.equals("arm64") ? "arm64" : null;
    if (system == null || arch == null)
      throw new IOException("Unsupported release host: " + osName + " " + osArch);
    String host = system + "-" + arch;
    var targets =
        JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonArray();
    ReleaseTarget selected = null;
    for (var target : targets) {
      var definition = target.getAsJsonObject();
      if (!definition.get("target").getAsString().equals(host)) continue;
      if (selected != null) throw new IOException("Duplicate release target: " + host);
      selected =
          new ReleaseTarget(
              host, definition.get("asset").getAsString(), definition.has("standalone"));
    }
    if (selected == null) throw new IOException("No release target for " + host);
    return selected;
  }
}
