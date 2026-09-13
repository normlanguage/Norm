package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Path;

public final class FileArtifactCacheProbe {
  public static void main(String[] args) throws Exception {
    var cache = new FileArtifactCache(Path.of(args[0]), 10, 100000);
    byte[] payload = new byte[4096];
    java.util.Arrays.fill(payload, (byte) 42);
    var key = Sha256Digest.compute(payload);
    for (int attempt = 0; attempt < 40; attempt++) {
      cache.write(key, payload);
      if (!java.util.Arrays.equals(payload, cache.read(key).orElseThrow()))
        throw new AssertionError("cache published incomplete content");
    }
  }
}
