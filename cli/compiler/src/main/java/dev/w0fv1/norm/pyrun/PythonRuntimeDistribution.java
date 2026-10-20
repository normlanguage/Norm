package dev.w0fv1.norm.pyrun;

import dev.w0fv1.norm.value.Sha256Digest;
import java.net.URI;
import java.util.Objects;

public record PythonRuntimeDistribution(
    String version,
    PythonRuntimePlatform platform,
    URI archive,
    Sha256Digest integrity,
    String executable) {
  public PythonRuntimeDistribution {
    Objects.requireNonNull(version);
    Objects.requireNonNull(platform);
    Objects.requireNonNull(archive);
    Objects.requireNonNull(integrity);
    Objects.requireNonNull(executable);
  }

  String identity() {
    return version + "/" + platform.key() + "/" + integrity.value();
  }
}
