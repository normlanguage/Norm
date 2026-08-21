package dev.w0fv1.norm.cli.component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class VersionProvider {
  private static final String VERSION = loadVersion();

  private VersionProvider() {}

  public static String current() {
    return VERSION;
  }

  private static String loadVersion() {
    try (InputStream stream =
        VersionProvider.class.getResourceAsStream(
            "/dev/w0fv1/norm/cli/component/version.properties")) {
      if (stream == null) {
        throw new IllegalStateException("Norm version resource is missing");
      }
      Properties properties = new Properties();
      properties.load(stream);
      String version = properties.getProperty("version");
      if (version == null || version.isBlank()) {
        throw new IllegalStateException("Norm version is missing from its resource");
      }
      return version;
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read the Norm version", exception);
    }
  }
}
