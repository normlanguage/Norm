package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CompilerArtifactIdentity {
  private static String identity;

  private CompilerArtifactIdentity() {}

  public static synchronized String current() throws IOException {
    if (identity != null) return identity;
    var writer = new CanonicalWriter().writeString(System.getProperty("java.runtime.version"));
    Path location;
    try {
      location =
          Path.of(
              CompilerArtifactIdentity.class
                  .getProtectionDomain()
                  .getCodeSource()
                  .getLocation()
                  .toURI());
    } catch (java.net.URISyntaxException exception) {
      throw new IOException("cannot identify compiler artifacts", exception);
    }
    if (Files.isDirectory(location)) {
      try (var files = Files.walk(location)) {
        for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
          writer
              .writeString(location.relativize(file).toString())
              .writeString(Sha256Digest.compute(file).value());
        }
      }
    } else {
      writer.writeString(Sha256Digest.compute(location).value());
    }
    try (var manifest =
        CompilerArtifactIdentity.class.getResourceAsStream("/toolchain-artifacts.json")) {
      if (manifest != null) writer.writeBytes(manifest.readAllBytes());
    }
    identity = Sha256Digest.compute(writer.toByteArray()).value();
    return identity;
  }
}
