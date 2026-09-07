package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeApplicationDeliveryTest {
  @TempDir Path directory;

  @Test
  void archivesTheCompleteRuntimeWithoutBuildDiagnostics() throws Exception {
    Path image = Files.writeString(directory.resolve("web.norm.exe"), "native");
    Path library = Files.writeString(directory.resolve("java.dll"), "library");
    Files.writeString(directory.resolve("debug.pdb"), "symbols");
    Path zip = directory.resolve("app.zip");
    NativeApplicationDelivery.archive(
        new NativeBuildArtifacts(directory, image, List.of(image, library)), zip);
    try (var archive = new ZipFile(zip.toFile())) {
      assertEquals(
          List.of("application.exe", "java.dll"), archive.stream().map(e -> e.getName()).toList());
      assertEquals(
          "native",
          new String(archive.getInputStream(archive.getEntry("application.exe")).readAllBytes()));
    }
  }
}
