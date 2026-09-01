package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.w0fv1.norm.value.ModuleArchiveFormat;
import dev.w0fv1.norm.value.ModuleRepositoryCoordinate;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

final class JavaBindingWorkspaceTest {
  @Test
  void keepsEveryBindingAsACompleteSingleRootNarWorkspace() throws Exception {
    Path workspace = Path.of("").toAbsolutePath().normalize();
    while (workspace != null && !Files.isDirectory(workspace.resolve("java-binding"))) {
      workspace = workspace.getParent();
    }
    assertNotNull(workspace, "workspace root is unavailable");
    Path bindings = workspace.resolve("java-binding");
    List<Path> projects;
    try (var entries = Files.list(bindings)) {
      projects = entries.filter(Files::isDirectory).sorted().toList();
    }
    assertTrue(projects.size() >= 10);
    Set<String> javaRoots = new HashSet<>();
    for (Path project : projects) {
      assertTrue(Files.isRegularFile(project.resolve("README.md")), project.toString());
      List<Path> modules;
      List<Path> nars;
      List<Path> poms;
      List<Path> jars;
      Set<String> authoredSources;
      try (var files = Files.walk(project)) {
        List<Path> all = files.filter(Files::isRegularFile).toList();
        modules = new ArrayList<>();
        for (Path path : all) {
          if (path.getFileName().toString().equals("module.norm")
              && Files.readString(path).contains("binding: jarBinding")) {
            modules.add(path);
          }
        }
        nars = all.stream().filter(path -> path.getFileName().toString().endsWith(".nar")).toList();
        poms = all.stream().filter(path -> path.getFileName().toString().endsWith(".pom")).toList();
        jars = all.stream().filter(path -> path.getFileName().toString().endsWith(".jar")).toList();
        authoredSources =
            all.stream()
                .filter(path -> path.getFileName().toString().endsWith(".norm"))
                .filter(path -> !path.getFileName().toString().equals("module.norm"))
                .map(project::relativize)
                .map(Path::toString)
                .map(path -> "sources/" + path.replace('\\', '/'))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
      }
      assertEquals(1, modules.size(), project.toString());
      assertEquals(1, nars.size(), project.toString());
      assertTrue(poms.isEmpty(), project.toString());
      assertTrue(!jars.isEmpty(), project.toString());
      try (ZipFile archive = new ZipFile(nars.getFirst().toFile())) {
        var manifestEntry = archive.getEntry("module.json");
        assertNotNull(manifestEntry, nars.getFirst().toString());
        JsonObject manifest;
        try (var reader = new InputStreamReader(archive.getInputStream(manifestEntry))) {
          manifest = JsonParser.parseReader(reader).getAsJsonObject();
        }
        assertEquals(ModuleArchiveFormat.FORMAT_VERSION, manifest.get("formatVersion").getAsInt());
        assertNotNull(archive.getEntry("binding/java-api.json"), nars.getFirst().toString());
        assertTrue(
            archive.stream().noneMatch(entry -> authoredSources.contains(entry.getName())),
            nars.getFirst().toString());
        JsonObject module = manifest.getAsJsonObject("module");
        JsonObject jar = manifest.getAsJsonObject("jar");
        assertNotNull(jar, nars.getFirst().toString());
        String javaRoot =
            jar.get("group").getAsString()
                + ":"
                + jar.get("artifact").getAsString()
                + ":"
                + jar.get("version").getAsString();
        assertTrue(javaRoots.add(javaRoot), javaRoot);
        ModuleRepositoryCoordinate coordinate =
            ModuleRepositoryCoordinate.from(
                new dev.w0fv1.norm.value.ModuleCoordinate(
                    module.get("name").getAsString(), module.get("version").getAsInt()));
        Path expected =
            project
                .resolve("artifacts")
                .resolve(coordinate.group().replace('.', java.io.File.separatorChar))
                .resolve(coordinate.artifact())
                .resolve(coordinate.version())
                .resolve(
                    coordinate.artifact()
                        + "-"
                        + coordinate.version()
                        + ModuleArchiveFormat.FILE_SUFFIX)
                .toAbsolutePath()
                .normalize();
        assertEquals(expected, nars.getFirst().toAbsolutePath().normalize());
      }
    }
  }
}
