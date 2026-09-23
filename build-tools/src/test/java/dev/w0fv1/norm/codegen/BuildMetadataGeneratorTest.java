package dev.w0fv1.norm.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildMetadataGeneratorTest {
  @TempDir Path directory;

  @Test
  void writesTheSingleVersionAndGraalVmSource() throws Exception {
    BuildMetadataGenerator.generate("0.24.0", "25.1.3", directory);

    assertEquals(
        """
        package dev.w0fv1.norm.value;

        public final class BuildMetadata {
          public static final String VERSION = "0.24.0";
          public static final String GRAALVM_VERSION = "25.1.3";

          private BuildMetadata() {}
        }
        """,
        Files.readString(directory.resolve("dev/w0fv1/norm/value/BuildMetadata.java")));
  }

  @Test
  void escapesVersionsAsJavaStringLiterals() throws Exception {
    BuildMetadataGenerator.generate("0.24.0\"quoted", "25\\1", directory);

    String source = Files.readString(directory.resolve("dev/w0fv1/norm/value/BuildMetadata.java"));
    assertTrue(source.contains("VERSION = \"0.24.0\\\"quoted\";"));
    assertTrue(source.contains("GRAALVM_VERSION = \"25\\\\1\";"));
  }

  @Test
  void rejectsIncompleteInvocation() {
    assertThrows(IllegalArgumentException.class, () -> BuildMetadataGenerator.main(new String[2]));
  }
}
