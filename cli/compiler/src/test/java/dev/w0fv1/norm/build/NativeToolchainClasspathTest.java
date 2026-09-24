package dev.w0fv1.norm.build;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.jvm.MavenJarIdentity;
import dev.w0fv1.norm.jvm.ResolvedJarArtifact;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeToolchainClasspathTest {
  @TempDir Path directory;

  @Test
  void retainsHostedAndExecutionClosuresWithoutRemovingSharedArtifacts() throws Exception {
    Path runtime = Files.writeString(directory.resolve("runtime.jar"), "runtime");
    Path hosted = Files.writeString(directory.resolve("hosted.jar"), "hosted");
    Path shared = Files.writeString(directory.resolve("shared.jar"), "shared");
    Path tooling = Files.writeString(directory.resolve("tool.jar"), "tool");
    Path application = Files.createDirectory(directory.resolve("classes"));
    var manifest = new com.google.gson.JsonObject();
    manifest.addProperty("schemaVersion", 2);
    var artifacts = new com.google.gson.JsonArray();
    for (Path path : List.of(runtime, hosted, shared, tooling)) {
      var artifact = new com.google.gson.JsonObject();
      String name = path.getFileName().toString().replace(".jar", "");
      artifact.addProperty("file", path.getFileName().toString());
      artifact.addProperty("group", "sample");
      artifact.addProperty("artifact", name);
      artifact.addProperty("version", "1");
      var storage = new com.google.gson.JsonObject();
      storage.addProperty("kind", "sealed");
      storage.addProperty("sha256", Sha256Digest.compute(path).value());
      artifact.add("storage", storage);
      var components = new com.google.gson.JsonArray();
      components.add("sample:" + name + ":1");
      if (name.equals("tool")) components.add("sample:embedded:1");
      artifact.add("components", components);
      artifacts.add(artifact);
    }
    manifest.add("artifacts", artifacts);
    manifest.add(
        "dependencies",
        com.google.gson.JsonParser.parseString(
            """
        {"sample:runtime:1":["sample:metadata:1"], "sample:metadata:1":["sample:shared:1"], "sample:hosted:1":["sample:shared:1"],
         "sample:shared:1":[], "sample:tool:1":["sample:shared:1"], "sample:embedded:1":[]}
        """));
    manifest.add(
        "purposes",
        com.google.gson.JsonParser.parseString(
            """
        {"execution":["sample:runtime:1"], "hosted":["sample:hosted:1"], "tooling":["sample:tool:1"]}
        """));
    Path catalog = Files.writeString(directory.resolve("catalog.json"), manifest.toString());
    var paths = List.of(application, runtime, hosted, shared, tooling);
    var plan = NativeToolchainClasspath.plan(catalog, paths);
    assertEquals(List.of(application), plan.unmanaged());
    assertEquals(2, plan.graphs().size());
    assertTrue(plan.graphs().stream().allMatch(graph -> graph.artifacts().size() == 3));
    var runtimeGraph =
        plan.graphs().stream()
            .filter(graph -> graph.root().file().equals(runtime))
            .findFirst()
            .orElseThrow();
    assertTrue(
        runtimeGraph.edges().stream()
            .anyMatch(
                edge ->
                    edge.from().equals(runtimeGraph.root().identity())
                        && edge.to().canonical().equals("maven:sample:shared:1")));
    assertThrows(
        java.io.IOException.class,
        () ->
            NativeToolchainClasspath.plan(catalog, List.of(application, runtime, shared, tooling)));
    manifest.getAsJsonObject("purposes").getAsJsonArray("execution").add("sample:embedded:1");
    Files.writeString(catalog, manifest.toString());
    var mergedPlan = NativeToolchainClasspath.plan(catalog, paths);
    assertEquals(3, mergedPlan.graphs().size());
    assertTrue(mergedPlan.graphs().stream().anyMatch(graph -> graph.root().file().equals(tooling)));
    var selected =
        new java.util.ArrayList<>(
            mergedPlan.graphs().stream()
                .flatMap(graph -> graph.artifacts().stream())
                .distinct()
                .toList());
    assertEquals(4, selected.size());
    assertDoesNotThrow(() -> mergedPlan.validate(selected));
    var replacement =
        new ResolvedJarArtifact(
            new MavenJarIdentity(new MavenArtifactCoordinate("sample", "embedded", "2")),
            Files.writeString(directory.resolve("replacement.jar"), "replacement"),
            Sha256Digest.compute("replacement".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    var conflict =
        assertThrows(
            java.io.IOException.class,
            () -> {
              var conflicting = new java.util.ArrayList<>(selected);
              conflicting.add(replacement);
              mergedPlan.validate(conflicting);
            });
    assertTrue(conflict.getCause().getMessage().contains("cannot be independently replaced"));
    assertTrue(conflict.getMessage().contains("cannot be independently replaced"));
    Files.writeString(shared, "changed");
    assertThrows(java.io.IOException.class, () -> NativeToolchainClasspath.plan(catalog, paths));
  }

  @Test
  void systemLibraryTracksLiveBytesButRejectsTargetAndModuleShapeChanges() throws Exception {
    Path system = automaticJar("system.jar", "sample.system", "first");
    Path replacement = automaticJar("replacement.jar", "sample.system", "second");
    Path wrongModule = automaticJar("wrong.jar", "sample.other", "second");
    Path installed = Files.createSymbolicLink(directory.resolve("sample.system.jar"), system);
    var manifest = new com.google.gson.JsonObject();
    manifest.addProperty("schemaVersion", 2);
    var artifact = new com.google.gson.JsonObject();
    artifact.addProperty("file", installed.getFileName().toString());
    artifact.addProperty("group", "sample");
    artifact.addProperty("artifact", "system");
    artifact.addProperty("version", "1");
    var components = new com.google.gson.JsonArray();
    components.add("sample:system:1");
    artifact.add("components", components);
    var storage = new com.google.gson.JsonObject();
    storage.addProperty("kind", "system");
    storage.addProperty("target", system.toString());
    storage.addProperty("automatic", true);
    var moduleRequires = new com.google.gson.JsonArray();
    java.lang.module.ModuleFinder.of(system)
        .findAll()
        .iterator()
        .next()
        .descriptor()
        .requires()
        .stream()
        .filter(
            requirement ->
                !requirement
                    .modifiers()
                    .contains(java.lang.module.ModuleDescriptor.Requires.Modifier.STATIC))
        .sorted(java.util.Comparator.comparing(java.lang.module.ModuleDescriptor.Requires::name))
        .forEach(
            requirement -> {
              var entry = new com.google.gson.JsonObject();
              entry.addProperty("name", requirement.name());
              entry.addProperty(
                  "transitive",
                  requirement
                      .modifiers()
                      .contains(java.lang.module.ModuleDescriptor.Requires.Modifier.TRANSITIVE));
              moduleRequires.add(entry);
            });
    storage.add("moduleRequires", moduleRequires);
    artifact.add("storage", storage);
    var artifacts = new com.google.gson.JsonArray();
    artifacts.add(artifact);
    manifest.add("artifacts", artifacts);
    manifest.add(
        "dependencies", com.google.gson.JsonParser.parseString("{\"sample:system:1\":[]}"));
    manifest.add(
        "purposes",
        com.google.gson.JsonParser.parseString(
            "{\"execution\":[\"sample:system:1\"],\"hosted\":[]}"));

    var first =
        NativeToolchainClasspath.plan(manifest, List.of(installed)).graphs().getFirst().root();
    Files.copy(replacement, system, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    var changed =
        NativeToolchainClasspath.plan(manifest, List.of(installed)).graphs().getFirst().root();
    assertNotEquals(first.storagePath(), changed.storagePath());
    assertThrows(
        java.io.IOException.class,
        () -> new dev.w0fv1.norm.value.FileSnapshot(installed, first.content()).verify());
    Files.delete(installed);
    Files.createSymbolicLink(installed, replacement);
    assertThrows(
        java.io.IOException.class,
        () -> NativeToolchainClasspath.plan(manifest, List.of(installed)));
    Files.delete(installed);
    Files.createSymbolicLink(installed, system);
    Files.copy(wrongModule, system, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    assertThrows(
        java.io.IOException.class,
        () -> NativeToolchainClasspath.plan(manifest, List.of(installed)));
    Path explicit = explicitJar("module-explicit.jar", "java.logging", false);
    Files.copy(explicit, system, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    assertThrows(
        java.io.IOException.class,
        () -> NativeToolchainClasspath.plan(manifest, List.of(installed)));
    storage.addProperty("automatic", false);
    var explicitRequires = new com.google.gson.JsonArray();
    var baseRequirement = new com.google.gson.JsonObject();
    baseRequirement.addProperty("name", "java.base");
    baseRequirement.addProperty("transitive", false);
    explicitRequires.add(baseRequirement);
    var loggingRequirement = new com.google.gson.JsonObject();
    loggingRequirement.addProperty("name", "java.logging");
    loggingRequirement.addProperty("transitive", false);
    explicitRequires.add(loggingRequirement);
    storage.add("moduleRequires", explicitRequires);
    assertDoesNotThrow(() -> NativeToolchainClasspath.plan(manifest, List.of(installed)));
    Path transitiveRequires = explicitJar("module-transitive.jar", "java.logging", true);
    Files.copy(transitiveRequires, system, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    assertThrows(
        java.io.IOException.class,
        () -> NativeToolchainClasspath.plan(manifest, List.of(installed)));
    Path changedRequires = explicitJar("module-changed.jar", "java.sql", false);
    Files.copy(changedRequires, system, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    assertThrows(
        java.io.IOException.class,
        () -> NativeToolchainClasspath.plan(manifest, List.of(installed)));
    Files.delete(system);
    assertThrows(
        java.io.IOException.class,
        () -> NativeToolchainClasspath.plan(manifest, List.of(installed)));
  }

  private Path automaticJar(String name, String module, String content) throws Exception {
    Path jar = directory.resolve(name);
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Automatic-Module-Name", module);
    try (var output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
      output.putNextEntry(new JarEntry("sample/Content.txt"));
      output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      output.closeEntry();
    }
    return jar;
  }

  private Path explicitJar(String name, String required, boolean transitive) throws Exception {
    Path source =
        Files.writeString(
            Files.createDirectory(directory.resolve(name + "-source")).resolve("module-info.java"),
            "module sample.system { requires "
                + (transitive ? "transitive " : "")
                + required
                + "; }");
    Path classes = Files.createDirectory(directory.resolve(name + "-classes"));
    int result =
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classes.toString(), source.toString());
    assertEquals(0, result);
    Path jar = directory.resolve(name);
    try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("module-info.class"));
      output.write(Files.readAllBytes(classes.resolve("module-info.class")));
      output.closeEntry();
    }
    return jar;
  }
}
