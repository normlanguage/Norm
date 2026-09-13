package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.core.store.CompilerArtifactIdentity;
import dev.w0fv1.norm.core.store.FileArtifactCache;
import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class JarApiCache {
  private final FileArtifactCache artifacts;

  public JarApiCache(Path directory) throws IOException {
    artifacts = new FileArtifactCache(directory, 256, 256L * 1024 * 1024);
  }

  public JarApiSchema scan(ResolvedJarGraph graph, List<String> selectedTypes, boolean surfaceOnly)
      throws IOException {
    if (surfaceOnly && selectedTypes.isEmpty()) return new JarApiSchema(List.of());
    for (var artifact : graph.artifacts())
      new FileSnapshot(artifact.file(), artifact.content()).verify();
    var writer =
        new CanonicalWriter()
            .writeTag("jar-api-1")
            .writeString(CompilerArtifactIdentity.current())
            .writeString(graph.contentId().value())
            .writeString(graph.root().identity().canonical())
            .writeBoolean(surfaceOnly)
            .writeInt(selectedTypes.size());
    selectedTypes.forEach(writer::writeString);
    var key = Sha256Digest.compute(writer.toByteArray());
    var stored = artifacts.read(key);
    if (stored.isPresent())
      return PortableObjectCodec.decode(stored.orElseThrow(), JarApiSchema.class);
    var scanner = new JarApiScanner();
    var schema =
        surfaceOnly
            ? scanner.scanSurface(graph, selectedTypes)
            : scanner.scan(graph, selectedTypes);
    for (var artifact : graph.artifacts())
      new FileSnapshot(artifact.file(), artifact.content()).verify();
    artifacts.write(key, PortableObjectCodec.encode(schema));
    return schema;
  }
}
