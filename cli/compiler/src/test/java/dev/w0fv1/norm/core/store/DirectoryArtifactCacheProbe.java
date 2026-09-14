package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DirectoryArtifactCacheProbe {
  public static void main(String[] args) throws Exception {
    var cache = new DirectoryArtifactCache(Path.of(args[0]), 1, 1024);
    try (var lease =
        cache.acquire(
            Sha256Digest.compute(new byte[] {1}),
            root -> true,
            root -> {
              if (args.length > 1) {
                Files.writeString(root.resolve("value"), "partial");
                System.out.println(root);
                System.out.flush();
                System.in.read();
              }
              Files.writeString(root.resolve("value"), "child");
            })) {
      System.out.println(lease.path());
      System.out.flush();
      if (args.length == 1) System.in.read();
    }
  }
}
