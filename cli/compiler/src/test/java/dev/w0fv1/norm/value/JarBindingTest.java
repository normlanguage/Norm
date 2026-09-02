package dev.w0fv1.norm.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class JarBindingTest {
  private static final String DIGEST = "0123456789abcdef".repeat(4);

  @Test
  void moduleCarriesOneOptionalRootJarBinding() {
    JarBinding binding =
        new JarBinding(
            new MavenJarTarget(
                new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0"),
                Optional.of(Sha256Digest.parse(DIGEST))),
            List.of(new JarBindingType("StringUtils", List.of("isBlank", "reverse"))));

    ModuleDescriptor descriptor =
        new ModuleDescriptor(
            new ModuleCoordinate("commons.lang", 1),
            List.of("StringUtils"),
            List.of(),
            Optional.of(binding));

    assertEquals(Optional.of(binding), descriptor.binding());
    assertEquals(List.of("StringUtils"), descriptor.exports());
    assertEquals(List.of("isBlank", "reverse"), binding.api().getFirst().members());
  }

  @Test
  void bindingModuleCanExportOrdinaryNormSourcesAfterItsJavaSurface() {
    JarBinding binding =
        new JarBinding(
            new LocalJarTarget("lib/sample.jar", Optional.empty()),
            List.of(new JarBindingType("sample.Entity", List.of())));

    ModuleDescriptor descriptor =
        new ModuleDescriptor(
            new ModuleCoordinate("orm", 1),
            List.of("Entity", "Repository"),
            List.of(),
            Optional.of(binding));

    assertEquals(List.of("Entity", "Repository"), descriptor.exports());
  }

  @Test
  void jarBindingTypesRequireUniqueQualifiedTypesAndMembers() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new JarBindingType("StringUtils", List.of("reverse", "reverse")));
    assertThrows(
        IllegalArgumentException.class, () -> new JarBindingType("not valid", List.of("reverse")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JarBindingType(
                "StringUtils",
                List.of("reverse"),
                List.of(new JarBindingOverload("reverse", List.of("java.lang.String")))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JarBindingType(
                "StringUtils",
                List.of(),
                List.of(
                    new JarBindingOverload("reverse", List.of("java.lang.String")),
                    new JarBindingOverload("reverse", List.of("java.lang.String")))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new JarBinding(
                new LocalJarTarget("lib/sample.jar", Optional.empty()),
                List.of(
                    new JarBindingType("Tools", List.of("run")),
                    new JarBindingType("Tools", List.of("stop")))));
  }

  @Test
  void localTargetsRequirePortableModuleRelativePaths() {
    assertEquals(
        "lib/commons-lang3.jar",
        new LocalJarTarget("lib\\commons-lang3.jar", Optional.empty()).path());
    assertThrows(
        IllegalArgumentException.class,
        () -> new LocalJarTarget("../commons-lang3.jar", Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LocalJarTarget("C:\\lib\\commons-lang3.jar", Optional.empty()));
  }

  @Test
  void sha256DigestHasCanonicalIdentity() {
    Sha256Digest digest = Sha256Digest.parse(DIGEST.toUpperCase());

    assertEquals(DIGEST, digest.value());
    assertEquals("sha256:" + DIGEST, digest.toString());
    assertThrows(IllegalArgumentException.class, () -> Sha256Digest.parse("abc"));
  }

  @Test
  void mavenTargetRequiresAStableVersion() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new MavenArtifactCoordinate("org.example", "sample", "1.+"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new MavenArtifactCoordinate("org.example", "sample", "1.0-SNAPSHOT"));
    assertTrue(new MavenArtifactCoordinate("org.example", "sample", "1.0.0").isFixedVersion());
  }
}
