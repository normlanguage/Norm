package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.ModuleRepositoryId;
import dev.w0fv1.norm.value.ModuleRequirement;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NormPackageResolverTest {
  @TempDir Path temporaryDirectory;

  @Test
  void downloadsAndCachesANormPackageFromItsSelectedRepository() throws Exception {
    Path remote = temporaryDirectory.resolve("remote");
    Path archive = remote.resolve("sample/library/1/library-1.nar");
    Files.createDirectories(archive.getParent());
    Files.writeString(archive, "immutable nar");
    Files.writeString(
        archive.resolveSibling("library-1.nar.sha256"), Sha256Digest.compute(archive).value());
    Path cache = temporaryDirectory.resolve("cache");
    var requirement = new ModuleRequirement("github", "sample.library", 1, false);

    Path resolved;
    try (var resolver =
        new NormPackageResolver(
            temporaryDirectory.resolve("local"),
            cache,
            Map.of(ModuleRepositoryId.GITHUB, remote.toUri()))) {
      resolved = resolver.resolve(requirement);
    }

    assertEquals(
        cache.resolve("github/sample/library/1/library-1.nar").toAbsolutePath().normalize(),
        resolved);
    assertEquals("immutable nar", Files.readString(resolved));
    Files.delete(archive);
    try (var resolver =
        new NormPackageResolver(
            temporaryDirectory.resolve("local"),
            cache,
            Map.of(ModuleRepositoryId.GITHUB, remote.toUri()))) {
      assertEquals(resolved, resolver.resolve(requirement));
    }
  }

  @Test
  void rejectsContentThatDoesNotMatchThePublishedDigest() throws Exception {
    Path remote = temporaryDirectory.resolve("remote-corrupt");
    Path archive = remote.resolve("sample/library/1/library-1.nar");
    Files.createDirectories(archive.getParent());
    Files.writeString(archive, "changed nar");
    Files.writeString(archive.resolveSibling("library-1.nar.sha256"), "0".repeat(64));

    try (var resolver =
        new NormPackageResolver(
            temporaryDirectory.resolve("local"),
            temporaryDirectory.resolve("cache-corrupt"),
            Map.of(ModuleRepositoryId.GITHUB, remote.toUri()))) {
      IOException exception =
          assertThrows(
              IOException.class,
              () -> resolver.resolve(new ModuleRequirement("github", "sample.library", 1, false)));

      assertTrue(exception.getMessage().contains("integrity"));
    }
  }

  @Test
  void rejectsUnknownRepositoryIdentities() throws Exception {
    try (var resolver =
        new NormPackageResolver(
            temporaryDirectory.resolve("local"), temporaryDirectory.resolve("cache"), Map.of())) {
      IOException exception =
          assertThrows(
              IOException.class,
              () -> resolver.resolve(new ModuleRequirement("missing", "sample.library", 1, false)));

      assertTrue(exception.getMessage().contains("unknown Norm package repository 'missing'"));
    }
  }
}
