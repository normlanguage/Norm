package dev.w0fv1.norm.pyrun;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

public final class PythonRuntimeCatalog {
  private static final String RESOURCE = "/pyrun/python-runtime.json";
  private final String version;
  private final Map<PythonRuntimePlatform, PythonRuntimeDistribution> distributions;

  private PythonRuntimeCatalog(
      String version, Map<PythonRuntimePlatform, PythonRuntimeDistribution> distributions) {
    this.version = version;
    this.distributions = Map.copyOf(distributions);
  }

  public static PythonRuntimeCatalog current() throws IOException {
    try (var stream = PythonRuntimeCatalog.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) throw new IOException("Python runtime catalog is unavailable");
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        String version = root.get("version").getAsString();
        EnumMap<PythonRuntimePlatform, PythonRuntimeDistribution> distributions =
            new EnumMap<>(PythonRuntimePlatform.class);
        JsonObject artifacts = root.getAsJsonObject("artifacts");
        for (PythonRuntimePlatform platform : PythonRuntimePlatform.values()) {
          JsonObject item = artifacts.getAsJsonObject(platform.key());
          if (item == null) throw new IOException("Python runtime catalog omits " + platform.key());
          distributions.put(
              platform,
              new PythonRuntimeDistribution(
                  version,
                  platform,
                  URI.create(item.get("url").getAsString()),
                  Sha256Digest.parse(item.get("sha256").getAsString()),
                  item.get("executable").getAsString()));
        }
        return new PythonRuntimeCatalog(version, distributions);
      }
    }
  }

  public String version() {
    return version;
  }

  public PythonRuntimeDistribution distribution(PythonRuntimePlatform platform) {
    PythonRuntimeDistribution result = distributions.get(platform);
    if (result == null)
      throw new IllegalArgumentException("No Python runtime for " + platform.key());
    return result;
  }
}
