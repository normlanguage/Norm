package dev.w0fv1.norm.packages;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.project.ProjectInputSnapshot;
import dev.w0fv1.norm.value.ModuleRequirement;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageResolutionInputsTest {
  @TempDir Path directory;

  @Test
  void tracksIntegrityMetadataAndHigherPriorityLocalPackage() throws Exception {
    Path local = directory.resolve("local");
    Path cache = directory.resolve("cache");
    Path relative = Path.of("sample/lib/1/lib-1.nar");
    Path archive = cache.resolve("github").resolve(relative);
    Files.createDirectories(archive.getParent());
    Files.writeString(archive, "package");
    Path digest = archive.resolveSibling("lib-1.nar.sha256");
    String integrity = Sha256Digest.compute(archive).value();
    Files.writeString(digest, integrity);
    try (var resolver = new NormPackageResolver(local, cache)) {
      assertEquals(
          archive,
          resolver.resolve(new ModuleRequirement("github", "sample.lib", 1, false)).archive());
      var observed =
          resolver.resolve(new ModuleRequirement("github", "sample.lib", 1, false)).inputs();
      var inputs = new ProjectInputSnapshot(observed.files(), List.of(), observed.absent());
      assertTrue(inputs.matches());
      Files.writeString(digest, Sha256Digest.compute(new byte[] {1}).value());
      assertFalse(inputs.matches());
      Files.writeString(digest, integrity);
      assertTrue(inputs.matches());
      Path override = local.resolve(relative);
      Files.createDirectories(override.getParent());
      Files.writeString(override, "override");
      assertFalse(inputs.matches());
      assertEquals(
          override,
          resolver.resolve(new ModuleRequirement("github", "sample.lib", 1, false)).archive());
    }
  }

  @Test
  void returnsResolutionInputsWithEachArchiveWithoutRetainingAnotherResolution() throws Exception {
    Path local = directory.resolve("local");
    Path first = local.resolve("sample/first/1/first-1.nar");
    Path second = local.resolve("sample/second/1/second-1.nar");
    Files.createDirectories(first.getParent());
    Files.createDirectories(second.getParent());
    Files.writeString(first, "first");
    Files.writeString(second, "second");
    try (var resolver = new NormPackageResolver(local, directory.resolve("cache"))) {
      var left = resolver.resolve(new ModuleRequirement("github", "sample.first", 1, false));
      var right = resolver.resolve(new ModuleRequirement("github", "sample.second", 1, false));

      assertEquals(first, left.archive());
      assertEquals(second, right.archive());
      assertEquals(
          List.of(first), left.inputs().files().stream().map(file -> file.path()).toList());
      assertEquals(
          List.of(second), right.inputs().files().stream().map(file -> file.path()).toList());
      assertThrows(UnsupportedOperationException.class, () -> left.inputs().files().clear());
    }
  }

  @Test
  void capturesMissingPackageCandidatesWhenResolutionFails() throws Exception {
    Path local = directory.resolve("local");
    Path cache = directory.resolve("cache");
    Path candidate = local.resolve("sample/lib/1/lib-1.nar");
    try (var resolver = new NormPackageResolver(local, cache)) {
      var failure =
          assertThrows(
              PackageResolutionException.class,
              () -> resolver.resolve(new ModuleRequirement("missing", "sample.lib", 1, false)));
      assertTrue(failure.inputs().absent().contains(candidate));
      var inputs =
          new ProjectInputSnapshot(failure.inputs().files(), List.of(), failure.inputs().absent());
      assertTrue(inputs.matches());
      Files.createDirectories(candidate.getParent());
      Files.writeString(candidate, "new package");
      assertFalse(inputs.matches());
    }
  }
}
