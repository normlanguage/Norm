package dev.w0fv1.norm.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ToolchainArtifactCatalogGeneratorTest {
  @TempDir Path directory;

  @Test
  void writesResolvedArtifactsAndMergedComponentOwnership() throws Exception {
    Path provider = Files.writeString(directory.resolve("provider.jar"), "provider");
    Path hosted = Files.writeString(directory.resolve("hosted.jar"), "hosted");
    var input =
        new ToolchainArtifactCatalogGenerator.Input(
            List.of(
                new ToolchainArtifactCatalogGenerator.Artifact(provider, "sample:provider:1"),
                new ToolchainArtifactCatalogGenerator.Artifact(hosted, "sample:hosted:1")),
            List.of("sample:provider:1", "sample:hosted:1"),
            Map.of(
                "sample:provider:1", List.of("sample:embedded:1"),
                "sample:embedded:1", List.of(),
                "sample:hosted:1", List.of()),
            Map.of(
                "execution", List.of("sample:provider:1"),
                "hosted", List.of("sample:hosted:1"),
                "tooling", List.of()),
            Map.of("sample:provider", List.of("sample:embedded")));
    Path output = directory.resolve("generated/toolchain-artifacts.json");

    ToolchainArtifactCatalogGenerator.generate(input, output);

    var catalog = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
    assertEquals(2, catalog.get("schemaVersion").getAsInt());
    assertEquals(
        "provider.jar",
        catalog.getAsJsonArray("artifacts").get(1).getAsJsonObject().get("file").getAsString());
    assertEquals(
        List.of("sample:embedded:1", "sample:provider:1"),
        catalog
            .getAsJsonArray("artifacts")
            .get(1)
            .getAsJsonObject()
            .getAsJsonArray("components")
            .asList()
            .stream()
            .map(value -> value.getAsString())
            .toList());
    assertEquals(
        64,
        catalog
            .getAsJsonArray("artifacts")
            .get(1)
            .getAsJsonObject()
            .getAsJsonObject("storage")
            .get("sha256")
            .getAsString()
            .length());
    assertEquals(2, catalog.getAsJsonArray("roots").size());
    assertEquals(1, catalog.getAsJsonObject("purposes").getAsJsonArray("execution").size());
  }

  @Test
  void recordsSystemLinkTargetAndAutomaticModuleShape() throws Exception {
    Path system = directory.resolve("system.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Automatic-Module-Name", "sample.system");
    try (var archive = new JarOutputStream(Files.newOutputStream(system), manifest)) {
      archive.finish();
    }
    Path installed = Files.createSymbolicLink(directory.resolve("sample.system.jar"), system);
    var input =
        new ToolchainArtifactCatalogGenerator.Input(
            List.of(new ToolchainArtifactCatalogGenerator.Artifact(installed, "sample:system:1")),
            List.of("sample:system:1"),
            Map.of("sample:system:1", List.of()),
            Map.of("execution", List.of("sample:system:1")),
            Map.of());
    Path output = directory.resolve("catalog.json");

    ToolchainArtifactCatalogGenerator.generate(input, output);

    var catalog = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
    var storage =
        catalog.getAsJsonArray("artifacts").get(0).getAsJsonObject().getAsJsonObject("storage");
    assertEquals("system", storage.get("kind").getAsString());
    assertEquals(
        system.toAbsolutePath().normalize().toString(), storage.get("target").getAsString());
    assertEquals(true, storage.get("automatic").getAsBoolean());
    var requirement = storage.getAsJsonArray("moduleRequires").get(0).getAsJsonObject();
    assertEquals("java.base", requirement.get("name").getAsString());
    assertEquals(false, requirement.get("transitive").getAsBoolean());
  }

  @Test
  void rejectsAmbiguousResolvedArtifacts() throws Exception {
    Path first =
        Files.writeString(
            Files.createDirectory(directory.resolve("first")).resolve("shared.jar"), "one");
    Path second =
        Files.writeString(
            Files.createDirectory(directory.resolve("second")).resolve("shared.jar"), "two");
    var input =
        new ToolchainArtifactCatalogGenerator.Input(
            List.of(
                new ToolchainArtifactCatalogGenerator.Artifact(first, "sample:first:1"),
                new ToolchainArtifactCatalogGenerator.Artifact(second, "sample:second:1")),
            List.of("sample:first:1"),
            Map.of("sample:first:1", List.of(), "sample:second:1", List.of()),
            Map.of(
                "execution", List.of("sample:first:1"), "hosted", List.of(), "tooling", List.of()),
            Map.of());

    assertThrows(
        IllegalArgumentException.class,
        () -> ToolchainArtifactCatalogGenerator.generate(input, directory.resolve("catalog.json")));
  }
}
