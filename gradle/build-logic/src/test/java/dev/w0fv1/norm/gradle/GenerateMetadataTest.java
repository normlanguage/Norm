package dev.w0fv1.norm.gradle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateMetadataTest {
  @TempDir Path directory;

  @Test
  void passesTheOutputDirectoryToTheSharedGenerator() throws Exception {
    var project = ProjectBuilder.builder().build();
    var task = project.getTasks().create("metadata", NormCompilerPlugin.GenerateMetadata.class);
    task.getNormVersion().set("0.24.0");
    task.getGraalVersion().set("25.1.3");
    task.getDestination().set(directory.toFile());
    task.generate();
    assertTrue(Files.isRegularFile(directory.resolve("dev/w0fv1/norm/value/BuildMetadata.java")));
    assertFalse(Files.exists(directory.resolve("dev/w0fv1/norm/value/BuildMetadata.java/dev")));
  }
}
