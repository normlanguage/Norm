package dev.w0fv1.norm.packaging;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RuntimeModuleAssemblerTest {
  private static final List<String> COMPONENTS =
      List.of(
          "maven-resolver-provider",
          "maven-model-builder",
          "maven-model",
          "maven-repository-metadata",
          "maven-artifact",
          "maven-builder-support");

  @TempDir Path directory;

  @Test
  void assemblesOneDeterministicResolverModuleAndPreservesRuntimeMetadata() throws Exception {
    Path compiler = compiler();
    List<Path> dependencies = new ArrayList<>();
    for (int i = 0; i < COMPONENTS.size(); i++) {
      Map<String, String> entries = new LinkedHashMap<>();
      entries.put("example/Part" + i + ".class", "part" + i);
      entries.put("META-INF/NOTICE", "Notice " + i);
      entries.put("META-INF/DEPENDENCIES", "Dependency " + i);
      if (i < 2) entries.put("META-INF/sisu/javax.inject.Named", "example.Part" + i + "\n");
      dependencies.add(
          jar(
              COMPONENTS.get(i) + "-3.9.16.jar",
              COMPONENTS.get(i).replace('-', '.'),
              "3.9.16",
              entries));
    }
    Path other =
        jar(
            "gson-2.14.0.jar",
            "com.google.gson",
            null,
            Map.of("com/google/gson/Gson.class", "gson"));
    dependencies.add(other);

    Path first = directory.resolve("first");
    Path second = directory.resolve("second");
    RuntimeModuleAssembler.assemble(compiler, dependencies, first, RuntimeStorage.SEALED);
    RuntimeModuleAssembler.assemble(compiler, dependencies, second, RuntimeStorage.SEALED);

    Path merged = first.resolve("lib/maven.resolver.provider.jar");
    assertTrue(Files.isRegularFile(merged));
    assertArrayEquals(
        Files.readAllBytes(merged),
        Files.readAllBytes(second.resolve("lib").resolve(merged.getFileName())));
    assertArrayEquals(
        Files.readAllBytes(other), Files.readAllBytes(first.resolve("lib/com.google.gson.jar")));
    assertTrue(Files.isRegularFile(first.resolve("lib/compiler-0.24.0.jar")));
    try (var archive = new JarFile(compiler.toFile())) {
      assertArrayEquals(
          archive.getInputStream(archive.getJarEntry("META-INF/LICENSE")).readAllBytes(),
          Files.readAllBytes(first.resolve("LICENSE")));
      assertArrayEquals(
          archive.getInputStream(archive.getJarEntry("META-INF/LICENSING.md")).readAllBytes(),
          Files.readAllBytes(first.resolve("LICENSING.md")));
    }
    assertFalse(Files.exists(first.resolve("lib/maven-model-3.9.16.jar")));
    try (var archive = new JarFile(merged.toFile())) {
      assertEquals(
          "maven.resolver.provider",
          archive.getManifest().getMainAttributes().getValue("Automatic-Module-Name"));
      assertEquals(
          "3.9.16", archive.getManifest().getMainAttributes().getValue("Implementation-Version"));
      assertEquals("part0", contents(archive, "example/Part0.class"));
      assertEquals("part5", contents(archive, "example/Part5.class"));
      assertTrue(contents(archive, "META-INF/sisu/javax.inject.Named").contains("example.Part0"));
      assertTrue(contents(archive, "META-INF/sisu/javax.inject.Named").contains("example.Part1"));
      assertTrue(contents(archive, "META-INF/NOTICE").contains("Notice 0"));
      assertTrue(contents(archive, "META-INF/NOTICE").contains("Notice 5"));
    }
  }

  @Test
  void rejectsConflictingClassesWithoutPublishingAnOutput() throws Exception {
    Path compiler = compiler();
    List<Path> dependencies = new ArrayList<>();
    for (String component : COMPONENTS) {
      dependencies.add(
          jar(
              component + "-3.9.16.jar",
              component.replace('-', '.'),
              "3.9.16",
              Map.of("example/Duplicate.class", component)));
    }
    Path output = directory.resolve("output");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            RuntimeModuleAssembler.assemble(compiler, dependencies, output, RuntimeStorage.SEALED));
    assertFalse(Files.exists(output));
  }

  @Test
  void assemblesSystemJarsWithSharedFileNames() throws Exception {
    Path compiler = compiler();
    List<Path> dependencies = new ArrayList<>();
    for (int i = 0; i < COMPONENTS.size(); i++) {
      String component = COMPONENTS.get(i);
      dependencies.add(
          jar(
              component + ".jar",
              component.replace('-', '.'),
              "3.9.11",
              Map.of("example/Component" + i + ".class", component)));
    }
    Path plexus =
        jar(
            "plexus-utils.jar",
            "org.codehaus.plexus.util",
            null,
            Map.of("plexus/Utils.class", "plexus"));
    Path graalvm =
        jar(
            "graalvm-utils.jar",
            "org.graalvm.buildtools.utils",
            null,
            Map.of("graalvm/Utils.class", "graalvm"));
    Path plexusDirectory = Files.createDirectory(directory.resolve("plexus"));
    Path graalvmDirectory = Files.createDirectory(directory.resolve("graalvm"));
    plexus = Files.move(plexus, plexusDirectory.resolve("utils.jar"));
    graalvm = Files.move(graalvm, graalvmDirectory.resolve("utils.jar"));
    dependencies.add(plexus);
    dependencies.add(graalvm);

    Path output = directory.resolve("output");
    RuntimeModuleAssembler.assemble(compiler, dependencies, output, RuntimeStorage.SEALED);

    assertTrue(Files.isRegularFile(output.resolve("lib/maven.resolver.provider.jar")));
    assertTrue(Files.isRegularFile(output.resolve("lib/org.codehaus.plexus.util.jar")));
    assertTrue(Files.isRegularFile(output.resolve("lib/org.graalvm.buildtools.utils.jar")));
  }

  @Test
  void assemblesDependenciesBeforeCompilerJarExists() throws Exception {
    List<Path> dependencies = new ArrayList<>();
    for (String component : COMPONENTS) {
      dependencies.add(
          jar(
              component + ".jar",
              component.replace('-', '.'),
              "3.9.11",
              Map.of("example/" + component + ".class", component)));
    }
    Path output = directory.resolve("dependencies");

    Map<Path, List<Path>> ownership =
        RuntimeModuleAssembler.assembleDependencies(dependencies, output, RuntimeStorage.SEALED);

    assertTrue(Files.isRegularFile(output.resolve("lib/maven.resolver.provider.jar")));
    assertFalse(Files.exists(output.resolve("lib/compiler-0.24.0.jar")));
    assertEquals(6, ownership.get(output.resolve("lib/maven.resolver.provider.jar")).size());
  }

  @Test
  void switchesStorageModesWithoutChangingSystemInputs() throws Exception {
    Path compiler = compiler();
    List<Path> dependencies = new ArrayList<>();
    for (String component : COMPONENTS)
      dependencies.add(
          jar(
              component + ".jar",
              component.replace('-', '.'),
              "3.9.11",
              Map.of("example/" + component + ".class", component)));
    Path system =
        jar("system.jar", "example.system", null, Map.of("example/System.class", "system"));
    dependencies.add(system);
    byte[] original = Files.readAllBytes(system);
    Path output = directory.resolve("switching");

    RuntimeModuleAssembler.assemble(compiler, dependencies, output, RuntimeStorage.SEALED);
    Path installed = output.resolve("lib/example.system.jar");
    var catalogInput =
        new ToolchainArtifactCatalogGenerator.Input(
            List.of(new ToolchainArtifactCatalogGenerator.Artifact(installed, "sample:system:1")),
            List.of("sample:system:1"),
            Map.of("sample:system:1", List.of()),
            Map.of("execution", List.of("sample:system:1")),
            Map.of());
    Path catalog = directory.resolve("catalog.json");
    ToolchainArtifactCatalogGenerator.generate(catalogInput, catalog);
    assertEquals(
        "sealed",
        JsonParser.parseString(Files.readString(catalog))
            .getAsJsonObject()
            .getAsJsonArray("artifacts")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("storage")
            .get("kind")
            .getAsString());
    assertFalse(Files.isSymbolicLink(installed));
    assertArrayEquals(original, Files.readAllBytes(installed));

    RuntimeModuleAssembler.assemble(compiler, dependencies, output, RuntimeStorage.SYSTEM);
    assertTrue(Files.isSymbolicLink(installed));
    assertEquals(system.toAbsolutePath().normalize(), Files.readSymbolicLink(installed));
    assertFalse(Files.isSymbolicLink(output.resolve("lib/maven.resolver.provider.jar")));
    assertArrayEquals(original, Files.readAllBytes(system));
    ToolchainArtifactCatalogGenerator.generate(catalogInput, catalog);
    var systemStorage =
        JsonParser.parseString(Files.readString(catalog))
            .getAsJsonObject()
            .getAsJsonArray("artifacts")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("storage");
    assertEquals("system", systemStorage.get("kind").getAsString());
    assertFalse(systemStorage.has("sha256"));

    RuntimeModuleAssembler.assemble(compiler, dependencies, output, RuntimeStorage.SEALED);
    assertFalse(Files.isSymbolicLink(installed));
    assertArrayEquals(original, Files.readAllBytes(installed));
    assertArrayEquals(original, Files.readAllBytes(system));
    ToolchainArtifactCatalogGenerator.generate(catalogInput, catalog);
    var sealedStorage =
        JsonParser.parseString(Files.readString(catalog))
            .getAsJsonObject()
            .getAsJsonArray("artifacts")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("storage");
    assertEquals("sealed", sealedStorage.get("kind").getAsString());
    assertFalse(sealedStorage.has("target"));
  }

  @Test
  void rejectsDifferentJarsWithTheSameModuleName() throws Exception {
    Path compiler = compiler();
    List<Path> dependencies = new ArrayList<>();
    for (String component : COMPONENTS) {
      dependencies.add(
          jar(
              component + ".jar",
              component.replace('-', '.'),
              "3.9.11",
              Map.of("example/Component.class", component)));
    }
    dependencies.add(
        jar("first.jar", "example.shared", null, Map.of("example/First.class", "first")));
    dependencies.add(
        jar("second.jar", "example.shared", null, Map.of("example/Second.class", "second")));
    Path output = directory.resolve("output");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            RuntimeModuleAssembler.assemble(compiler, dependencies, output, RuntimeStorage.SEALED));
    assertFalse(Files.exists(output));
  }

  @Test
  void rejectsMismatchedMavenProviderVersions() throws Exception {
    Path compiler = compiler();
    List<Path> dependencies = new ArrayList<>();
    for (int i = 0; i < COMPONENTS.size(); i++) {
      String component = COMPONENTS.get(i);
      dependencies.add(
          jar(
              component + ".jar",
              component.replace('-', '.'),
              i == 0 ? "3.9.12" : "3.9.11",
              Map.of("example/Component" + i + ".class", component)));
    }
    Path output = directory.resolve("output");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            RuntimeModuleAssembler.assemble(compiler, dependencies, output, RuntimeStorage.SEALED));
    assertFalse(Files.exists(output));
  }

  @Test
  void rejectsCompilerJarWithoutRequiredLicense() throws Exception {
    Path compiler =
        jar("compiler-0.24.0.jar", null, null, Map.of("compiler/Main.class", "compiler"));
    List<Path> dependencies = new ArrayList<>();
    for (String component : COMPONENTS) {
      dependencies.add(
          jar(
              component + ".jar",
              component.replace('-', '.'),
              "3.9.11",
              Map.of("example/Component" + component + ".class", component)));
    }
    Path output = directory.resolve("output");

    IOException failure =
        assertThrows(
            IOException.class,
            () ->
                RuntimeModuleAssembler.assemble(
                    compiler, dependencies, output, RuntimeStorage.SEALED));

    assertTrue(failure.getMessage().contains("META-INF/LICENSE"));
    assertFalse(Files.exists(output));
  }

  private Path compiler() throws IOException {
    return jar(
        "compiler-0.24.0.jar",
        null,
        null,
        Map.of(
            "compiler/Main.class", "compiler",
            "META-INF/LICENSE", "license",
            "META-INF/LICENSING.md", "licensing"));
  }

  private Path jar(String name, String moduleName, String version, Map<String, String> entries)
      throws IOException {
    Path file = directory.resolve(name);
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    if (moduleName != null)
      manifest.getMainAttributes().putValue("Automatic-Module-Name", moduleName);
    if (version != null) manifest.getMainAttributes().putValue("Implementation-Version", version);
    try (var archive = new JarOutputStream(Files.newOutputStream(file), manifest)) {
      for (var entry : entries.entrySet()) {
        archive.putNextEntry(new JarEntry(entry.getKey()));
        archive.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        archive.closeEntry();
      }
    }
    return file;
  }

  private static String contents(JarFile archive, String name) throws IOException {
    try (var input = archive.getInputStream(archive.getJarEntry(name))) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
