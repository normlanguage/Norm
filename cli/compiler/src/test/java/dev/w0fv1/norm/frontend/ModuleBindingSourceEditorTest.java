package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.Sha256Digest;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModuleBindingSourceEditorTest {
  @TempDir Path temporaryDirectory;

  @Test
  void insertsAResolvedDigestIntoTheDirectMavenTargetDeclaration() {
    SourceFile source =
        SourceFile.of(
            temporaryDirectory.resolve("module.norm"),
            """
            Module module() {
              return module(
                name: "commons.lang",
                version: 1,
                binding: jarBinding(
                  target: mavenJar(
                    group: "org.apache.commons",
                    artifact: "commons-lang3",
                    version: "3.20.0"
                  ),
                  api: [jarType(name: "StringUtils", members: ["reverse"])]
                )
              )
            }
            """);
    var target =
        new MavenJarTarget(
            new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0"),
            Optional.empty());
    Sha256Digest digest = Sha256Digest.parse("0123456789abcdef".repeat(4));

    String updated = new ModuleBindingSourceEditor().withDigest(source, target, digest);

    assertTrue(updated.contains("resolution: sha256(\"" + digest.value() + "\")"));
  }
}
