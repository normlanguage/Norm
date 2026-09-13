package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.core.store.CompilerArtifactIdentity;
import dev.w0fv1.norm.core.store.FileArtifactCache;
import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.value.FileSnapshot;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class JarGraphCache {
  private final FileArtifactCache artifacts;

  JarGraphCache(Path directory) throws IOException {
    artifacts = new FileArtifactCache(directory, 512, 32L * 1024 * 1024);
  }

  Optional<ResolvedJarGraph> read(MavenJarTarget target) throws IOException {
    if (target.resolution().isEmpty()) return Optional.empty();
    var content = target.resolution().orElseThrow();
    var stored = artifacts.read(key(target.coordinate(), content));
    if (stored.isEmpty()) return Optional.empty();
    var saved = PortableObjectCodec.decode(stored.orElseThrow(), ResolvedJarGraph.class);
    var graph = new ResolvedJarGraph(saved.root(), saved.artifacts(), saved.edges());
    if (!graph.contentId().equals(content)
        || !graph.root().identity().equals(new MavenJarIdentity(target.coordinate()))) {
      throw new IOException(
          "cached Maven graph identity mismatch: " + target.coordinate().notation());
    }
    for (var artifact : graph.artifacts()) {
      if (!Files.isRegularFile(artifact.file())) return Optional.empty();
      new FileSnapshot(artifact.file(), artifact.content()).verify();
    }
    return Optional.of(graph);
  }

  void write(MavenArtifactCoordinate coordinate, ResolvedJarGraph graph) throws IOException {
    artifacts.write(key(coordinate, graph.contentId()), PortableObjectCodec.encode(graph));
  }

  private static Sha256Digest key(MavenArtifactCoordinate coordinate, Sha256Digest content)
      throws IOException {
    return Sha256Digest.compute(
        new CanonicalWriter()
            .writeTag("maven-graph-1")
            .writeString(CompilerArtifactIdentity.current())
            .writeString(coordinate.notation())
            .writeString(content.value())
            .toByteArray());
  }
}
