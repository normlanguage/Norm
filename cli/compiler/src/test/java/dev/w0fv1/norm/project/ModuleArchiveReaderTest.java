package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.ModuleArchiveFormat;
import dev.w0fv1.norm.value.ModuleRepositoryId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModuleArchiveReaderTest {
  @TempDir Path temporaryDirectory;

  @Test
  void rejectsLegacySourceOnlyFormats() throws Exception {
    for (int version : new int[] {4, 5, 6}) {
      IOException exception =
          assertThrows(
              IOException.class,
              () -> new ModuleArchiveReader().read(archive(version, "\"repository\":\"github\",")));
      assertEquals("unsupported module archive format", exception.getMessage());
    }
  }

  @Test
  void rejectsDependenciesWithoutARepositoryIdentity() throws Exception {
    IOException exception =
        assertThrows(
            IOException.class,
            () -> new ModuleArchiveReader().read(archive(ModuleArchiveFormat.FORMAT_VERSION, "")));
    assertTrue(exception.getMessage().contains("invalid module archive"));
  }

  @Test
  void readsDependenciesWithTheirRepositoryIdentity() throws Exception {
    var dependency =
        new ModuleArchiveReader()
            .read(archive(ModuleArchiveFormat.FORMAT_VERSION, "\"repository\":\"github\","))
            .descriptor()
            .dependencies()
            .getFirst();
    assertEquals(ModuleRepositoryId.GITHUB, dependency.repository());
  }

  private Path archive(int version, String repository) throws Exception {
    Path archive =
        temporaryDirectory.resolve("module-" + version + "-" + repository.length() + ".nar");
    Path module = Files.createDirectories(temporaryDirectory.resolve("sample/library"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath, "Module module() { module(name: \"sample.library\", version: 1) }");
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    Path original;
    try (var compiler = environment.compilerSession();
        var projects = environment.projectLoader()) {
      original =
          new ModulePackager(projects, compiler)
              .packageModule(modulePath, temporaryDirectory.resolve("repository"))
              .archive();
    }
    try (var input = new ZipFile(original.toFile());
        var output = new ZipOutputStream(Files.newOutputStream(archive))) {
      for (var entry : input.stream().toList()) {
        byte[] bytes;
        try (var stream = input.getInputStream(entry)) {
          bytes = stream.readAllBytes();
        }
        if (entry.getName().equals("module.json")) {
          var manifest =
              JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
          manifest.addProperty("formatVersion", version);
          manifest
              .getAsJsonObject("module")
              .add(
                  "dependencies",
                  JsonParser.parseString(
                      "[{%s\"name\":\"sample.base\",\"version\":1,\"exported\":false}]"
                          .formatted(repository)));
          bytes = manifest.toString().getBytes(StandardCharsets.UTF_8);
        }
        output.putNextEntry(new ZipEntry(entry.getName()));
        output.write(bytes);
        output.closeEntry();
      }
    }
    return archive;
  }
}
