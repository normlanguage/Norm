package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record ArtifactFileSet(Map<Path, FileSnapshot> files) {
  public ArtifactFileSet {
    files = Map.copyOf(files);
    for (Path name : files.keySet()) {
      if (name.getRoot() != null
          || name.toString().isBlank()
          || !name.equals(name.normalize())
          || name.startsWith(".."))
        throw new IllegalArgumentException(
            "artifact file name must be relative and normalized: " + name);
    }
  }

  public DirectoryArtifactCache.Lease acquire(DirectoryArtifactCache cache) throws IOException {
    var writer = new CanonicalWriter().writeTag("artifact-file-set-1").writeInt(files.size());
    files.entrySet().stream()
        .sorted(
            java.util.Comparator.comparing(entry -> entry.getKey().toString().replace('\\', '/')))
        .forEach(
            entry ->
                writer
                    .writeString(entry.getKey().toString().replace('\\', '/'))
                    .writeString(entry.getValue().content().value()));
    return cache.acquire(
        Sha256Digest.compute(writer.toByteArray()),
        root -> {
          try (var existing = Files.walk(root)) {
            if (existing.filter(Files::isRegularFile).count() != files.size()) return false;
          }
          for (var entry : files.entrySet()) {
            new FileSnapshot(root.resolve(entry.getKey()), entry.getValue().content()).verify();
          }
          return true;
        },
        this::copyTo);
  }

  public void copyTo(Path directory) throws IOException {
    for (var entry : files.entrySet()) entry.getValue().copyTo(directory.resolve(entry.getKey()));
  }
}
