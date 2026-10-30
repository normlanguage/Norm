package dev.w0fv1.norm.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ApplicationEntry {
  private ApplicationEntry() {}

  public static Path resolve(Path input) throws IOException {
    Path normalized = Objects.requireNonNull(input, "input").toAbsolutePath().normalize();
    if (!Files.isDirectory(normalized)) return normalized;
    Path application = normalized.resolve("application.norm");
    if (Files.isRegularFile(application)) return application;
    Path main = normalized.resolve("main.norm");
    if (Files.isRegularFile(main)) return main;
    throw new IOException(
        "application directory contains neither application.norm nor main.norm: " + normalized);
  }
}
