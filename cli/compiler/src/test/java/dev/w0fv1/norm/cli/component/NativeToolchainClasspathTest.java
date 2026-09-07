package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.jvm.MavenJarIdentity;
import dev.w0fv1.norm.jvm.ResolvedJarArtifact;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    manifest.addProperty("schemaVersion", 1);
    var artifacts = new com.google.gson.JsonArray();
    for (Path path : List.of(runtime, hosted, shared, tooling)) {
      var artifact = new com.google.gson.JsonObject();
      String name = path.getFileName().toString().replace(".jar", "");
      artifact.addProperty("file", path.getFileName().toString());
      artifact.addProperty("group", "sample");
      artifact.addProperty("artifact", name);
      artifact.addProperty("version", "1");
      artifact.addProperty("sha256", Sha256Digest.compute(path).value());
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
}
