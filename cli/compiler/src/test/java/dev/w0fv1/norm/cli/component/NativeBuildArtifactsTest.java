package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeBuildArtifactsTest {
  @TempDir Path directory;

  @Test
  void preservesRuntimeLibrariesButNotDiagnostics() throws Exception {
    Path staging = Files.createDirectory(directory.resolve("staging"));
    Path image = Files.writeString(staging.resolve("app.exe"), "image");
    Files.writeString(staging.resolve("java.dll"), "library");
    Files.writeString(
        staging.resolve("build-artifacts.json"),
        """
        {"executables":["app.exe"],"jdk_libraries":["java.dll"],"build_info":["../report.json"]}
        """);
    Path output = directory.resolve("delivery/renamed.exe");
    var artifacts = NativeBuildArtifacts.read(staging, image);
    var delivered = artifacts.publishLibraries(output);
    assertEquals(
        java.util.Set.of(output, output.resolveSibling("java.dll")),
        java.util.Set.copyOf(delivered));
    assertEquals("library", Files.readString(output.resolveSibling("java.dll")));
    assertFalse(Files.exists(output));
    assertEquals(delivered, artifacts.publishLibraries(output));
    Files.writeString(output.resolveSibling("java.dll"), "unrelated");
    assertThrows(IOException.class, () -> artifacts.publishLibraries(output));
    assertEquals("unrelated", Files.readString(output.resolveSibling("java.dll")));
  }

  @Test
  void rejectsMissingAndEscapingRuntimeArtifacts() throws Exception {
    Path staging = Files.createDirectory(directory.resolve("staging"));
    Path image = Files.writeString(staging.resolve("app.exe"), "image");
    Files.writeString(directory.resolve("outside.dll"), "outside");
    for (String library : java.util.List.of("absent.dll", "../outside.dll")) {
      Files.writeString(
          staging.resolve("build-artifacts.json"),
          "{\"executables\":[\"app.exe\"],\"jdk_libraries\":[\"" + library + "\"]}");
      assertThrows(IOException.class, () -> NativeBuildArtifacts.read(staging, image));
    }
  }
}
