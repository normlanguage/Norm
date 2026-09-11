package dev.w0fv1.norm.build;

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

  @Test
  void validatesEveryLibraryConflictBeforePublishingAnyLibrary() throws Exception {
    Path staging = Files.createDirectories(directory.resolve("staging"));
    Path image = Files.writeString(staging.resolve("app.exe"), "new image");
    Path first = Files.writeString(staging.resolve("first.dll"), "new first");
    Path second = Files.writeString(staging.resolve("second.dll"), "new second");
    Path delivery = Files.createDirectories(directory.resolve("delivery"));
    Path output = Files.writeString(delivery.resolve("app.exe"), "previous image");
    Path conflict = Files.writeString(delivery.resolve("second.dll"), "unrelated");
    var artifacts =
        new NativeBuildArtifacts(staging, image, java.util.List.of(image, first, second));
    assertThrows(IOException.class, () -> artifacts.publishLibraries(output));
    assertFalse(Files.exists(delivery.resolve("first.dll")));
    assertEquals("unrelated", Files.readString(conflict));
    assertEquals("previous image", Files.readString(output));
  }

  @Test
  void reportsCopyFailureWithoutClaimingAtomicMultiFileDelivery() throws Exception {
    Path staging = Files.createDirectories(directory.resolve("staging"));
    Path image = Files.writeString(staging.resolve("app.exe"), "new image");
    Path first = Files.writeString(staging.resolve("first.dll"), "first library");
    Path nested = Files.createDirectories(staging.resolve("nested"));
    Path second = Files.writeString(nested.resolve("second.dll"), "second library");
    Path delivery = Files.createDirectories(directory.resolve("delivery"));
    Path output = Files.writeString(delivery.resolve("app.exe"), "previous image");
    Path blocked = Files.writeString(delivery.resolve("nested"), "existing file");
    var artifacts =
        new NativeBuildArtifacts(staging, image, java.util.List.of(image, first, second));
    assertThrows(IOException.class, () -> artifacts.publishLibraries(output));
    assertEquals("first library", Files.readString(delivery.resolve("first.dll")));
    assertEquals("existing file", Files.readString(blocked));
    assertEquals("previous image", Files.readString(output));
  }
}
