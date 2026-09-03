package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.ModuleRepositoryId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModuleArchiveReaderTest {
  @TempDir Path temporaryDirectory;

  @Test
  void readsVersionFourDependenciesFromTheOriginalGithubRepository() throws Exception {
    Path archive = archive(4, "");

    var dependency = new ModuleArchiveReader().read(archive).descriptor().dependencies().getFirst();

    assertEquals(ModuleRepositoryId.GITHUB, dependency.repository());
  }

  @Test
  void rejectsVersionFiveDependenciesWithoutARepositoryIdentity() throws Exception {
    IOException exception =
        assertThrows(IOException.class, () -> new ModuleArchiveReader().read(archive(5, "")));

    assertTrue(exception.getMessage().contains("invalid module archive"));
  }

  @Test
  void readsVersionFiveDependenciesWithTheirRepositoryIdentity() throws Exception {
    Path archive = archive(5, "\"repository\":\"github\",");

    var dependency = new ModuleArchiveReader().read(archive).descriptor().dependencies().getFirst();

    assertEquals(ModuleRepositoryId.GITHUB, dependency.repository());
  }

  private Path archive(int version, String repository) throws IOException {
    Path archive =
        temporaryDirectory.resolve("module-" + version + "-" + repository.length() + ".nar");
    String manifest =
        """
        {
          "formatVersion": %d,
          "module": {
            "name": "sample.library",
            "version": 1,
            "exports": [],
            "dependencies": [
              {%s"name":"sample.base","version":1,"exported":false}
            ]
          }
        }
        """
            .formatted(version, repository);
    try (var output = new ZipOutputStream(Files.newOutputStream(archive))) {
      output.putNextEntry(new ZipEntry("module.json"));
      output.write(manifest.getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
    }
    return archive;
  }
}
