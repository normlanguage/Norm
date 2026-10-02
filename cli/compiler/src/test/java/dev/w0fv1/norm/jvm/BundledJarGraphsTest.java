package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.JdkModuleTarget;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BundledJarGraphsTest {
  @TempDir Path temporaryDirectory;

  @Test
  void validatesBundledJdkMetadataAgainstTheRunningModule() throws Exception {
    ResolvedJarGraph graph;
    try (var resolver = new JarResolver(temporaryDirectory)) {
      graph =
          resolver.resolve(
              temporaryDirectory,
              new JarBinding(new JdkModuleTarget("java.base", java.util.Optional.empty())));
    }
    var binding =
        new ResolvedJarBinding(
            graph,
            new JarApiSchema(List.of()),
            new GeneratedJarBinding(
                List.of(),
                List.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of()));
    Path bundle = temporaryDirectory.resolve("jdk-bundle");
    BundledJarGraphs.write(bundle, List.of(binding));
    try (var resolver = JarResolver.bundled(bundle)) {
      assertEquals(
          graph.contentId(),
          resolver
              .resolve(
                  temporaryDirectory,
                  new JarBinding(
                      new JdkModuleTarget("java.base", java.util.Optional.of(graph.contentId()))))
              .contentId());
    }
    assertFalse(Files.exists(bundle.resolve(".norm-jdk")));
    Path falseMetadata = temporaryDirectory.resolve("false-metadata.jar");
    try (var output = new java.util.jar.JarOutputStream(Files.newOutputStream(falseMetadata))) {
      output.finish();
    }
    var falseArtifact =
        new ResolvedJarArtifact(
            new JdkModuleIdentity("java.base"), falseMetadata, Sha256Digest.compute(falseMetadata));
    var falseGraph = new ResolvedJarGraph(falseArtifact, List.of(falseArtifact), List.of());
    var falseBinding =
        new ResolvedJarBinding(
            falseGraph,
            new JarApiSchema(List.of()),
            new GeneratedJarBinding(
                List.of(),
                List.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of()));
    Path falseBundle = temporaryDirectory.resolve("false-bundle");
    BundledJarGraphs.write(falseBundle, List.of(falseBinding));
    var mismatch =
        assertThrows(java.io.IOException.class, () -> BundledJarGraphs.read(falseBundle));
    assertTrue(mismatch.getMessage().contains("does not match this runtime"));
  }

  @Test
  void preservesAResolvedGraphWithoutAMavenRepository() throws Exception {
    Path rootFile = temporaryDirectory.resolve("root.jar");
    Path dependencyFile = temporaryDirectory.resolve("dependency.jar");
    try (var jar = new java.util.jar.JarOutputStream(Files.newOutputStream(rootFile))) {
      jar.finish();
    }
    Files.writeString(dependencyFile, "dependency");
    ResolvedJarArtifact root = artifact("org.example", "root", "1.0", rootFile);
    ResolvedJarArtifact dependency = artifact("org.example", "dependency", "2.0", dependencyFile);
    ResolvedJarGraph graph =
        new ResolvedJarGraph(
            root,
            List.of(root, dependency),
            List.of(new JarDependencyEdge(root.identity(), dependency.identity())));
    ResolvedJarBinding binding =
        new ResolvedJarBinding(
            graph,
            new JarApiSchema(List.of()),
            new GeneratedJarBinding(
                List.of(),
                List.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of()));
    Path bundle = temporaryDirectory.resolve("bundle");

    BundledJarGraphs.write(bundle, List.of(binding));
    ResolvedJarGraph restored = BundledJarGraphs.read(bundle).get(graph.contentId());

    assertEquals(graph.contentId(), restored.contentId());
    assertEquals(
        graph.artifacts().stream().map(ResolvedJarArtifact::identity).toList(),
        restored.artifacts().stream().map(ResolvedJarArtifact::identity).toList());
    assertEquals(graph.edges(), restored.edges());
    assertEquals(
        "root",
        java.lang.module.ModuleFinder.of(restored.root().file())
            .findAll()
            .iterator()
            .next()
            .descriptor()
            .name());
    assertEquals(rootFile.getFileName(), restored.root().file().getFileName());
    Path manifestFile = bundle.resolve(BundledJarGraphs.MANIFEST);
    var manifest =
        com.google.gson.JsonParser.parseString(Files.readString(manifestFile)).getAsJsonObject();
    var bundledArtifact =
        manifest
            .getAsJsonArray("graphs")
            .get(0)
            .getAsJsonObject()
            .getAsJsonArray("artifacts")
            .get(0)
            .getAsJsonObject();
    for (String invalid : List.of("../root.jar", "..\\root.jar", "C:root.jar", "..", "")) {
      bundledArtifact.addProperty("fileName", invalid);
      Files.writeString(manifestFile, manifest.toString());
      assertThrows(java.io.IOException.class, () -> BundledJarGraphs.read(bundle));
    }
  }

  @Test
  void archiveRestoresWithoutProducerFilesAndRejectsTamperedArtifacts() throws Exception {
    Path file = Files.writeString(temporaryDirectory.resolve("root.jar"), "original");
    var artifact = artifact("sample", "root", "1", file);
    var binding =
        new ResolvedJarBinding(
            new ResolvedJarGraph(artifact, List.of(artifact), List.of()),
            new JarApiSchema(List.of()),
            new GeneratedJarBinding(
                List.of(),
                List.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of()));
    Path archive = temporaryDirectory.resolve("package.nar");
    try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
      BundledJarGraphs.writeArchive(output, binding);
    }
    Files.delete(file);
    var restored =
        BundledJarGraphs.readArchive(archive, temporaryDirectory.resolve("cache")).orElseThrow();
    assertEquals(
        "original", Files.readString(restored.get(binding.graph().contentId()).root().file()));
    Path tampered = temporaryDirectory.resolve("tampered.nar");
    try (var input = new java.util.zip.ZipFile(archive.toFile());
        var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(tampered))) {
      for (var entry : input.stream().toList()) {
        output.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
        if (entry.getName().endsWith(".jar"))
          output.write("changed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        else
          try (var content = input.getInputStream(entry)) {
            content.transferTo(output);
          }
        output.closeEntry();
      }
    }
    assertThrows(
        java.io.IOException.class,
        () -> BundledJarGraphs.readArchive(tampered, temporaryDirectory.resolve("cache")));
  }

  @Test
  void archiveRejectsEntriesOutsideItsExtractionRoot() throws Exception {
    Path archive = temporaryDirectory.resolve("invalid.nar");
    try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
      output.putNextEntry(new java.util.zip.ZipEntry("java/graphs.json"));
      output.write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      output.closeEntry();
      output.putNextEntry(new java.util.zip.ZipEntry("java/../../escape.jar"));
      output.closeEntry();
    }
    assertThrows(
        java.io.IOException.class,
        () -> BundledJarGraphs.readArchive(archive, temporaryDirectory.resolve("cache")));
    assertFalse(Files.exists(temporaryDirectory.resolve("escape.jar")));
  }

  @Test
  void rejectsChangedSourceBeforePublishingManifest() throws Exception {
    Path file = Files.writeString(temporaryDirectory.resolve("root.jar"), "original");
    var artifact = artifact("sample", "root", "1", file);
    var binding =
        new ResolvedJarBinding(
            new ResolvedJarGraph(artifact, List.of(artifact), List.of()),
            new JarApiSchema(List.of()),
            new GeneratedJarBinding(
                List.of(),
                List.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of()));
    Files.writeString(file, "changed");
    Path output = temporaryDirectory.resolve("bundle");
    assertThrows(java.io.IOException.class, () -> BundledJarGraphs.write(output, List.of(binding)));
    assertFalse(Files.exists(output.resolve(BundledJarGraphs.MANIFEST)));
  }

  @Test
  void repairsChangedDestinationUsingCapturedContentIdentity() throws Exception {
    Path file = Files.writeString(temporaryDirectory.resolve("root.jar"), "original");
    var artifact = artifact("sample", "root", "1", file);
    var binding =
        new ResolvedJarBinding(
            new ResolvedJarGraph(artifact, List.of(artifact), List.of()),
            new JarApiSchema(List.of()),
            new GeneratedJarBinding(
                List.of(),
                List.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.Map.of()));
    Path output = temporaryDirectory.resolve("bundle");
    BundledJarGraphs.write(output, List.of(binding));
    Path target = output.resolve("artifacts").resolve(artifact.storagePath());
    Files.writeString(target, "changed");
    BundledJarGraphs.write(output, List.of(binding));
    assertEquals("original", Files.readString(target));
    assertEquals(1, BundledJarGraphs.read(output).size());
  }

  private static ResolvedJarArtifact artifact(String group, String name, String version, Path file)
      throws Exception {
    return new ResolvedJarArtifact(
        new MavenJarIdentity(new MavenArtifactCoordinate(group, name, version)),
        file,
        Sha256Digest.compute(file));
  }
}
