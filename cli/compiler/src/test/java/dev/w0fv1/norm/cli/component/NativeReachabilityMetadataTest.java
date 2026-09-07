package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.jvm.GeneratedJarBinding;
import dev.w0fv1.norm.jvm.JarApiSchema;
import dev.w0fv1.norm.jvm.JarBindingClasspath;
import dev.w0fv1.norm.jvm.JarDependencyEdge;
import dev.w0fv1.norm.jvm.MavenJarIdentity;
import dev.w0fv1.norm.jvm.ResolvedJarArtifact;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class NativeReachabilityMetadataTest {
  @org.junit.jupiter.api.io.TempDir Path directory;

  @Test
  void recordsTheActualMetadataOriginInsteadOfTheCopiedArtifactVersion() throws Exception {
    var artifact =
        new ResolvedJarArtifact(
            new MavenJarIdentity(
                new MavenArtifactCoordinate("org.slf4j", "slf4j-simple", "2.0.18")),
            directory.resolve("slf4j-simple.jar"),
            Sha256Digest.compute(new byte[0]));
    var plan =
        JarBindingClasspath.prepare(
            List.of(binding(new ResolvedJarGraph(artifact, List.of(artifact), List.of()))));
    var result = new NativeReachabilityMetadata().prepare(plan, directory.resolve("metadata"));
    var manifest =
        com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(result.manifest()))
            .getAsJsonObject();
    var source = manifest.getAsJsonArray("configurations").get(0).getAsJsonObject();
    assertEquals("org.slf4j:slf4j-simple:2.0.18", source.get("artifact").getAsString());
    assertEquals("1.8.0-alpha0", source.get("metadataVersion").getAsString());
    assertEquals(false, source.get("versionTested").getAsBoolean());
    var actual =
        directory.resolve(
            "metadata/repository/org.slf4j/slf4j-simple/1.8.0-alpha0/reachability-metadata.json");
    assertEquals(
        Sha256Digest.compute(actual).value(),
        source.getAsJsonObject("files").get("reachability-metadata.json").getAsString());
  }

  @Test
  void recordsAnEmptySelection() throws Exception {
    var result =
        new NativeReachabilityMetadata()
            .prepare(JarBindingClasspath.prepare(List.of()), directory.resolve("empty"));
    var manifest =
        com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(result.manifest()))
            .getAsJsonObject();
    assertEquals(0, manifest.getAsJsonArray("configurations").size());
    assertEquals(0, result.configurationCount());
  }

  @Test
  void preservesOfficialTestedVersionAndOverrideDeclarations() throws Exception {
    var artifact =
        new ResolvedJarArtifact(
            new MavenJarIdentity(
                new MavenArtifactCoordinate("io.netty", "netty-handler", "4.1.80.Final")),
            directory.resolve("netty-handler.jar"),
            Sha256Digest.compute(new byte[0]));
    var plan =
        JarBindingClasspath.prepare(
            List.of(binding(new ResolvedJarGraph(artifact, List.of(artifact), List.of()))));
    var result = new NativeReachabilityMetadata().prepare(plan, directory.resolve("netty"));
    var manifest =
        com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(result.manifest()))
            .getAsJsonObject();
    var source = manifest.getAsJsonArray("configurations").get(0).getAsJsonObject();
    assertEquals(true, source.get("versionTested").getAsBoolean());
    assertEquals(true, source.get("override").getAsBoolean());
    assertEquals(false, source.get("sourceDirectoryPresent").getAsBoolean());
    assertEquals(0, source.getAsJsonObject("files").size());
    var properties = new java.util.Properties();
    try (var input =
        java.nio.file.Files.newInputStream(
            result
                .classpath()
                .resolve(
                    "META-INF/native-image/io.netty/netty-handler/4.1.80.Final/reachability-metadata.properties"))) {
      properties.load(input);
    }
    assertEquals("true", properties.getProperty("override"));
  }

  @Test
  void usesTheSameSelectedVersionsForApplicationAndToolchainMetadata() {
    var application = artifact("application", "1");
    var support = artifact("support", "1");
    var previous = artifact("library", "1");
    var selected = artifact("library", "2");
    var applicationGraph =
        new ResolvedJarGraph(
            application,
            List.of(application, previous),
            List.of(new JarDependencyEdge(application.identity(), previous.identity())));
    var supportGraph =
        new ResolvedJarGraph(
            support,
            List.of(support, selected),
            List.of(new JarDependencyEdge(support.identity(), selected.identity())));
    var plan =
        JarBindingClasspath.prepare(List.of(binding(applicationGraph)), List.of(supportGraph));
    assertEquals(
        Set.of("sample:application:1", "sample:support:1", "sample:library:2"),
        NativeReachabilityMetadata.coordinates(plan));
  }

  @Test
  void collectsOnlyTheSelectedVersionsAndTheirReachableDependencies() {
    var root = artifact("application", "1");
    var previous = artifact("library", "1");
    var selected = artifact("library", "2");
    var obsolete = artifact("obsolete", "1");
    var graph =
        new ResolvedJarGraph(
            root,
            List.of(root, previous, obsolete),
            List.of(
                new JarDependencyEdge(root.identity(), previous.identity()),
                new JarDependencyEdge(previous.identity(), obsolete.identity())));

    assertEquals(
        Set.of("sample:application:1", "sample:library:2"),
        NativeReachabilityMetadata.coordinates(
            List.of(
                binding(graph),
                binding(new ResolvedJarGraph(selected, List.of(selected), List.of())))));
  }

  private static ResolvedJarArtifact artifact(String name, String version) {
    return new ResolvedJarArtifact(
        new MavenJarIdentity(new MavenArtifactCoordinate("sample", name, version)),
        Path.of(name + "-" + version + ".jar"),
        Sha256Digest.compute((name + version).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  private static ResolvedJarBinding binding(ResolvedJarGraph graph) {
    return new ResolvedJarBinding(
        graph,
        new JarApiSchema(List.of()),
        new GeneratedJarBinding(List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of()));
  }
}
