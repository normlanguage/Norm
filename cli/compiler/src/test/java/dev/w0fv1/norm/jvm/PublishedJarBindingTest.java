package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PublishedJarBindingTest {
  @TempDir Path directory;

  @Test
  void bindingIdentityIsPortableAndRejectsDifferentModulesGraphsAndApis() throws Exception {
    Path file = Files.writeString(directory.resolve("library.jar"), "published dependency");
    var identity = new MavenJarIdentity(new MavenArtifactCoordinate("sample", "library", "1"));
    var artifact = new ResolvedJarArtifact(identity, file, Sha256Digest.compute(file));
    var graph = new ResolvedJarGraph(artifact, List.of(artifact), List.of());
    var descriptor = new ModuleDescriptor("sample.binding", 1, List.of());
    var api = new JarApiSchema(List.of());
    var generated =
        new GeneratedJarBinding(List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of());
    byte[] bytes =
        PublishedJarBinding.encode(descriptor, new ResolvedJarBinding(graph, api, generated));
    var decoded = PublishedJarBinding.decode(bytes, descriptor, api.apiId());
    Path moved =
        Files.copy(
            file, Files.createDirectories(directory.resolve("moved")).resolve("library.jar"));
    var relocated = new ResolvedJarArtifact(identity, moved, Sha256Digest.compute(moved));
    var relocatedGraph = new ResolvedJarGraph(relocated, List.of(relocated), List.of());
    assertEquals(generated, decoded.link(relocatedGraph).generated());
    assertArrayEquals(bytes, PublishedJarBinding.encode(descriptor, decoded.link(relocatedGraph)));
    assertThrows(
        IOException.class,
        () ->
            PublishedJarBinding.decode(
                bytes, new ModuleDescriptor("sample.other", 1, List.of()), api.apiId()));
    assertThrows(
        IOException.class,
        () -> PublishedJarBinding.decode(bytes, descriptor, Sha256Digest.compute(new byte[] {1})));
    var changed = new ResolvedJarArtifact(identity, moved, Sha256Digest.compute(new byte[] {2}));
    assertThrows(
        IOException.class,
        () -> decoded.link(new ResolvedJarGraph(changed, List.of(changed), List.of())));
  }
}
