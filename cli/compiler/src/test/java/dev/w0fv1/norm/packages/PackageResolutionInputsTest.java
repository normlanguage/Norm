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
          archive, resolver.resolve(new ModuleRequirement("github", "sample.lib", 1, false)));
      var observed = resolver.resolutionInputs();
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
          override, resolver.resolve(new ModuleRequirement("github", "sample.lib", 1, false)));
    }
  }
}
