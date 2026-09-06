package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeBuildReportTest {
  @Test
  void fingerprintsOrderedClasspathIncludingGeneratedFilesAndRequestedArguments() throws Exception {
    Path classes = Files.createDirectories(directory.resolve("generated"));
    Path generated = Files.write(classes.resolve("Controller.class"), new byte[] {1, 2});
    Path metadata = Files.createDirectories(classes.resolve("META-INF/native-image"));
    Path config = Files.writeString(metadata.resolve("reachability-metadata.json"), "{}");
    Path jar = Files.write(directory.resolve("compiler.jar"), new byte[] {3});
    Path archive = Files.write(directory.resolve("application.bin"), new byte[] {4});
    Path launcher = Files.writeString(directory.resolve("native-image.cmd"), "launcher");
    var arguments = java.util.List.of("-O2", "-cp", "exact classpath");
    try (var report = NativeBuildReport.create(directory.resolve("inputs.exe"), value -> {})) {
      report.buildInputs(arguments, java.util.List.of(classes, jar), archive, launcher);
      var snapshot =
          JsonParser.parseString(Files.readString(report.directory().resolve("build-inputs.json")))
              .getAsJsonObject();
      assertEquals(1, snapshot.get("schemaVersion").getAsInt());
      assertEquals("-O2", snapshot.getAsJsonArray("arguments").get(0).getAsString());
      var entries = snapshot.getAsJsonArray("classpath");
      assertEquals(2, entries.size());
      var files = entries.get(0).getAsJsonObject().getAsJsonArray("files");
      assertEquals(2, files.size());
      assertEquals("Controller.class", files.get(0).getAsJsonObject().get("path").getAsString());
      assertEquals(
          Sha256Digest.compute(generated).value(),
          files.get(0).getAsJsonObject().get("sha256").getAsString());
      assertEquals(
          Sha256Digest.compute(config).value(),
          files.get(1).getAsJsonObject().get("sha256").getAsString());
      assertEquals(
          Sha256Digest.compute(archive).value(),
          snapshot
              .getAsJsonObject("archive")
              .getAsJsonArray("files")
              .get(0)
              .getAsJsonObject()
              .get("sha256")
              .getAsString());
      Files.write(generated, new byte[] {9});
      report.buildInputs(arguments, java.util.List.of(classes, jar), archive, launcher);
      assertNotEquals(
          snapshot,
          JsonParser.parseString(
              Files.readString(report.directory().resolve("build-inputs.json"))));
      assertThrows(
          IOException.class,
          () ->
              report.buildInputs(
                  arguments,
                  java.util.List.of(directory.resolve("absent.jar")),
                  archive,
                  launcher));
    }
  }

  @Test
  void recordsCoreRetentionPredecessorsAndNames() throws Exception {
    var compiled =
        dev.w0fv1.norm.testing.NormTestKit.compile("Void main() { printLine(\"hello\") }");
    var original = compiled.program().orElseThrow().compilation().artifact();
    var analysis = dev.w0fv1.norm.core.CoreReachability.analyze(original, java.util.Set.of());
    try (var report = NativeBuildReport.create(directory.resolve("causes.exe"), value -> {})) {
      report.coreRetention(analysis);
      var manifest =
          JsonParser.parseString(
                  Files.readString(report.directory().resolve("core-retention.json")))
              .getAsJsonObject();
      var groups = manifest.getAsJsonObject("groups");
      assertEquals(analysis.causes().size(), groups.size());
      var root = groups.getAsJsonObject(original.entryDefinition().group().toString());
      assertEquals("APPLICATION_ENTRY", root.get("reason").getAsString());
      assertFalse(root.has("source"));
      assertTrue(root.getAsJsonArray("names").toString().contains("main"));
      assertTrue(root.getAsJsonArray("intrinsics").size() > 0);
    }
  }

  @Test
  void recordsSelectedJavaArtifactIdentityAndRejectsChangedContent() throws Exception {
    Path jar = Files.write(directory.resolve("library.jar"), new byte[] {1, 2, 3});
    var artifact =
        new dev.w0fv1.norm.jvm.ResolvedJarArtifact(
            new dev.w0fv1.norm.jvm.MavenJarIdentity(
                new dev.w0fv1.norm.value.MavenArtifactCoordinate("sample", "library", "2")),
            jar,
            Sha256Digest.compute(jar));
    try (var report = NativeBuildReport.create(directory.resolve("linked.exe"), value -> {})) {
      report.javaArtifacts(java.util.List.of(artifact));
      var manifest =
          JsonParser.parseString(
                  Files.readString(report.directory().resolve("java-artifacts.json")))
              .getAsJsonObject();
      var selected = manifest.getAsJsonArray("artifacts").get(0).getAsJsonObject();
      assertEquals(artifact.identity().canonical(), selected.get("identity").getAsString());
      assertEquals(artifact.content().value(), selected.get("sha256").getAsString());
      assertEquals(3, selected.get("bytes").getAsLong());
    }
    Files.write(jar, new byte[] {4, 5, 6});
    try (var report = NativeBuildReport.create(directory.resolve("changed.exe"), value -> {})) {
      assertThrows(IOException.class, () -> report.javaArtifacts(java.util.List.of(artifact)));
      assertFalse(Files.exists(report.directory().resolve("java-artifacts.json")));
    }
  }

  @Test
  void requestsImageHeapPartitionEvidence() throws Exception {
    try (var report = NativeBuildReport.create(directory.resolve("partitions.exe"), value -> {})) {
      assertTrue(report.arguments().contains("-H:+PrintImageHeapPartitionSizes"));
    }
  }

  @org.junit.jupiter.api.Test
  void recordsApplicationMethodIdentityAndJvmTarget() throws Exception {
    var id = dev.w0fv1.norm.core.DefinitionId.parse("0".repeat(64) + ":0");
    try (var report = NativeBuildReport.create(directory.resolve("sample.exe"), value -> {})) {
      report.applicationMethods(
          java.util.Map.of(
              id,
              new dev.w0fv1.norm.jvm.JavaApplicationMethodIndex.Target(
                  "sample.Controller", "hello", "()Ljava/lang/String;")));
      var metadata =
          com.google.gson.JsonParser.parseString(
                  java.nio.file.Files.readString(
                      report.directory().resolve("application-methods.json")))
              .getAsJsonObject();
      assertEquals(
          "sample.Controller", metadata.getAsJsonObject(id.toString()).get("owner").getAsString());
      assertEquals(
          "()Ljava/lang/String;",
          metadata.getAsJsonObject(id.toString()).get("descriptor").getAsString());
    }
  }

  @Test
  void identifiesComponentsPhysicallyMergedIntoOneArtifact() throws Exception {
    try (var report = NativeBuildReport.create(directory.resolve("app.exe"), ignored -> {})) {
      var manifest =
          JsonParser.parseString(
                  Files.readString(report.directory().resolve("toolchain-artifacts.json")))
              .getAsJsonObject();
      var owners = new java.util.HashMap<String, String>();
      for (var value : manifest.getAsJsonArray("artifacts")) {
        var artifact = value.getAsJsonObject();
        String file = artifact.get("file").getAsString();
        for (var component : artifact.getAsJsonArray("components")) {
          assertNull(owners.put(component.getAsString(), file));
        }
      }
      var provider =
          manifest.getAsJsonArray("artifacts").asList().stream()
              .map(com.google.gson.JsonElement::getAsJsonObject)
              .filter(
                  artifact ->
                      artifact.get("artifact").getAsString().equals("maven-resolver-provider"))
              .findFirst()
              .orElseThrow();
      assertEquals(6, provider.getAsJsonArray("components").size());
      for (String name :
          java.util.List.of(
              "org.apache.maven.model.Model",
              "org.apache.maven.artifact.Artifact",
              "org.apache.maven.building.Problem")) {
        Path jar =
            Path.of(
                Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI());
        assertEquals(provider.get("file").getAsString(), jar.getFileName().toString());
        assertEquals(provider.get("sha256").getAsString(), Sha256Digest.compute(jar).value());
      }
      for (var component : manifest.getAsJsonObject("dependencies").keySet()) {
        if (!owners.containsKey(component)) assertTrue(component.contains("-bom:"), component);
      }
    }
  }

  @Test
  void recordsResolvedToolchainArtifactIdentityAndContent() throws Exception {
    try (var report = NativeBuildReport.create(directory.resolve("app.exe"), ignored -> {})) {
      var manifest =
          JsonParser.parseString(
                  Files.readString(report.directory().resolve("toolchain-artifacts.json")))
              .getAsJsonObject();
      assertEquals(1, manifest.get("schemaVersion").getAsInt());
      var graph = manifest.getAsJsonObject("dependencies");
      var purposes = manifest.getAsJsonObject("purposes");
      assertTrue(
          purposes.getAsJsonArray("execution").asList().stream()
              .anyMatch(value -> value.getAsString().startsWith("org.objenesis:objenesis:")));
      assertTrue(
          purposes.getAsJsonArray("hosted").asList().stream()
              .anyMatch(value -> value.getAsString().startsWith("com.esotericsoftware:kryo:")));
      assertFalse(
          purposes.getAsJsonArray("execution").asList().stream()
              .anyMatch(value -> value.getAsString().startsWith("com.esotericsoftware:kryo:")));
      var reachable = new java.util.HashSet<String>();
      var categorized = new java.util.HashSet<String>();
      for (var purpose : purposes.entrySet()) {
        purpose.getValue().getAsJsonArray().forEach(value -> categorized.add(value.getAsString()));
      }
      assertEquals(
          manifest.getAsJsonArray("roots").asList().stream()
              .map(com.google.gson.JsonElement::getAsString)
              .collect(java.util.stream.Collectors.toSet()),
          categorized);
      var hostedClosure = new java.util.HashSet<String>();
      var hostedPending = new java.util.ArrayDeque<String>();
      purposes.getAsJsonArray("hosted").forEach(value -> hostedPending.add(value.getAsString()));
      while (!hostedPending.isEmpty()) {
        String coordinate = hostedPending.removeFirst();
        if (!hostedClosure.add(coordinate)) continue;
        graph.getAsJsonArray(coordinate).forEach(value -> hostedPending.add(value.getAsString()));
      }
      assertTrue(
          hostedClosure.stream().anyMatch(value -> value.startsWith("org.objenesis:objenesis:")));
      var pending = new java.util.ArrayDeque<String>();
      manifest.getAsJsonArray("roots").forEach(root -> pending.add(root.getAsString()));
      assertFalse(pending.isEmpty());
      while (!pending.isEmpty()) {
        String coordinate = pending.removeFirst();
        if (!reachable.add(coordinate)) continue;
        assertTrue(graph.has(coordinate), coordinate);
        graph
            .getAsJsonArray(coordinate)
            .forEach(dependency -> pending.add(dependency.getAsString()));
      }
      Path gson =
          Path.of(
              com.google.gson.Gson.class
                  .getProtectionDomain()
                  .getCodeSource()
                  .getLocation()
                  .toURI());
      var names = new java.util.HashSet<String>();
      boolean found = false;
      for (var element : manifest.getAsJsonArray("artifacts")) {
        var artifact = element.getAsJsonObject();
        String file = artifact.get("file").getAsString();
        assertTrue(names.add(file));
        assertEquals(file, Path.of(file).getFileName().toString());
        new dev.w0fv1.norm.value.MavenArtifactCoordinate(
            artifact.get("group").getAsString(),
            artifact.get("artifact").getAsString(),
            artifact.get("version").getAsString());
        assertTrue(
            reachable.contains(
                artifact.get("group").getAsString()
                    + ":"
                    + artifact.get("artifact").getAsString()
                    + ":"
                    + artifact.get("version").getAsString()));
        var hash = new dev.w0fv1.norm.value.Sha256Digest(artifact.get("sha256").getAsString());
        if (file.equals(gson.getFileName().toString())) {
          found = true;
          assertEquals("com.google.code.gson", artifact.get("group").getAsString());
          assertEquals("gson", artifact.get("artifact").getAsString());
          assertEquals(dev.w0fv1.norm.value.Sha256Digest.compute(gson), hash);
        }
      }
      assertTrue(found);
      String yaml =
          reachable.stream()
              .filter(
                  value ->
                      value.startsWith("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:"))
              .findFirst()
              .orElseThrow();
      assertTrue(
          graph.getAsJsonArray(yaml).asList().stream()
              .anyMatch(value -> value.getAsString().startsWith("org.yaml:snakeyaml:")));
    }
  }

  @TempDir Path directory;

  @Test
  void retainsIndependentBuildsAndLogsForTheSameExecutable() throws Exception {
    Path executable = directory.resolve("web.norm.exe");
    var messages = new ArrayList<String>();
    Path first;
    try (var report = NativeBuildReport.create(executable, messages::add)) {
      first = report.directory();
      report.accept("analysis started");
      assertTrue(
          report
              .arguments()
              .contains("-H:BuildOutputJSONFile=" + first.resolve("build-output.json")));
      assertTrue(report.arguments().contains("-H:DashboardDump=" + first.resolve("dashboard")));
      assertTrue(report.arguments().contains("-H:+DiagnosticsMode"));
      assertTrue(report.arguments().contains("-H:DiagnosticsDir=" + first.resolve("analysis")));
      assertTrue(report.arguments().contains("-H:+PrintAnalysisCallTree"));
      assertTrue(report.arguments().contains("-H:PrintAnalysisCallTreeType=CSV"));
    }
    try (var report = NativeBuildReport.create(executable, messages::add)) {
      assertNotEquals(first, report.directory());
      assertEquals(directory.resolve(".norm/build-reports/web.norm.exe"), first.getParent());
    }
    assertTrue(Files.readString(first.resolve("build.log")).contains("analysis started"));
    assertTrue(messages.contains("analysis started"));
    assertFalse(Files.exists(first.resolve("size.json")));
  }

  @Test
  void measuresActualFileSeparatelyFromPreLinkImageAndBindsReportToItsHash() throws Exception {
    Path executable = directory.resolve("hello.exe");
    Files.write(executable, new byte[128]);
    Path library = Files.write(directory.resolve("java.dll"), new byte[64]);
    try (var report = NativeBuildReport.create(executable, ignored -> {})) {
      Files.writeString(report.directory().resolve("build-output.json"), statistics());
      report.complete(executable, java.util.List.of(executable, library));
      var json =
          JsonParser.parseString(Files.readString(report.directory().resolve("size.json")))
              .getAsJsonObject();
      assertEquals(1, json.get("schemaVersion").getAsInt());
      assertEquals(128, json.get("executableBytes").getAsLong());
      assertEquals(192, json.get("deliveryBytes").getAsLong());
      assertEquals(2, json.getAsJsonArray("runtimeFiles").size());
      assertEquals(150, json.get("imageBytes").getAsLong());
      assertEquals(70, json.get("codeBytes").getAsLong());
      assertEquals(60, json.get("heapBytes").getAsLong());
      assertEquals(123, json.get("reachableMethods").getAsLong());
      assertEquals(Sha256Digest.compute(executable).value(), json.get("sha256").getAsString());
    }
  }

  @Test
  void missingStatisticsLeaveTheBuildLogWithoutASuccessReport() throws Exception {
    Path executable = directory.resolve("hello.exe");
    Files.write(executable, new byte[128]);
    Path diagnostics;
    try (var report = NativeBuildReport.create(executable, ignored -> {})) {
      diagnostics = report.directory();
      report.accept("Native Image exited with code 1");
      assertThrows(
          IOException.class, () -> report.complete(executable, java.util.List.of(executable)));
    }
    assertTrue(Files.readString(diagnostics.resolve("build.log")).contains("exited with code 1"));
    assertFalse(Files.exists(diagnostics.resolve("size.json")));
  }

  @Test
  void missingOrInvalidStatisticsAreNotReportedAsZero() throws Exception {
    Path executable = directory.resolve("hello.exe");
    Files.write(executable, new byte[128]);
    for (String content :
        new String[] {
          "{}", statistics().replace("150", "-1"), statistics().replace("150", "1.5")
        }) {
      try (var report = NativeBuildReport.create(executable, ignored -> {})) {
        Files.writeString(report.directory().resolve("build-output.json"), content);
        assertThrows(
            IOException.class, () -> report.complete(executable, java.util.List.of(executable)));
        assertFalse(Files.exists(report.directory().resolve("size.json")));
      }
    }
  }

  private static String statistics() {
    return """
        {"image_details":{"total_bytes":150,"code_area":{"bytes":70},"image_heap":{"bytes":60}},
         "analysis_results":{"methods":{"reachable":123,"reflection":12},"types":{"reachable":30,"reflection":5}}}
        """;
  }
}
