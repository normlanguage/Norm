package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class JarArtifactOwnershipTest {
  @Test
  void rejectsReplacingAComponentWhileItsPhysicalOwnerRemains() {
    var owner = artifact("bundle", "1", "bundle.jar");
    var embedded = new MavenArtifactCoordinate("sample", "embedded", "1");
    var ownership = new JarArtifactOwnership(owner, Set.of(embedded));
    var replacement = artifact("embedded", "2", "replacement.jar");
    var failure =
        assertThrows(
            IllegalArgumentException.class, () -> ownership.validate(List.of(owner, replacement)));
    assertTrue(failure.getMessage().contains("sample:embedded:1"));
    assertTrue(failure.getMessage().contains("sample:embedded:2"));
    assertDoesNotThrow(() -> ownership.validate(List.of(replacement)));
    assertDoesNotThrow(() -> ownership.validate(List.of(owner)));
  }

  @Test
  void doesNotTreatMatchingVersionsAsProofOfEquivalentPhysicalContent() {
    var owner = artifact("bundle", "1", "bundle.jar");
    var embedded = new MavenArtifactCoordinate("sample", "embedded", "1");
    var ownership = new JarArtifactOwnership(owner, Set.of(embedded));
    assertThrows(
        IllegalArgumentException.class,
        () -> ownership.validate(List.of(owner, artifact("embedded", "1", "standalone.jar"))));
    assertDoesNotThrow(
        () -> ownership.validate(List.of(owner, artifact("other", "1", "other.jar"))));
  }

  @Test
  void distinguishesClassifiedArtifactsFromTheOwnedMainArtifact() {
    var owner = artifact("bundle", "1", "bundle.jar");
    var embedded = new MavenArtifactCoordinate("sample", "embedded", "1");
    var attachment =
        new ResolvedJarArtifact(
            new MavenJarIdentity(embedded, "tests"),
            Path.of("tests.jar"),
            Sha256Digest.compute(new byte[] {1}));
    assertDoesNotThrow(
        () ->
            new JarArtifactOwnership(owner, Set.of(embedded)).validate(List.of(owner, attachment)));
  }

  private static ResolvedJarArtifact artifact(String name, String version, String file) {
    return new ResolvedJarArtifact(
        new MavenJarIdentity(new MavenArtifactCoordinate("sample", name, version)),
        Path.of(file),
        Sha256Digest.compute(file.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }
}
