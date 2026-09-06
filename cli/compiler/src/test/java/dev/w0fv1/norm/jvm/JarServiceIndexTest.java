package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JarServiceIndexTest {
  @TempDir Path directory;

  @Test
  void preservesServiceAndArtifactProvenanceWithoutDuplicateDeclarations() throws Exception {
    Path jar = directory.resolve("mixed.jar");
    try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("META-INF/services/javax.annotation.processing.Processor"));
      output.write(
          "# processor\nsample.Processor # inline\n\nsample.Processor\n"
              .getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
      output.putNextEntry(new JarEntry("META-INF/services/sample.RuntimeService"));
      output.write("sample.RuntimeProvider\n".getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
      output.putNextEntry(new JarEntry("META-INF/services/nested/ignored"));
      output.write("not.a.Service\n".getBytes(StandardCharsets.UTF_8));
      output.closeEntry();
    }
    var entries = JarServiceIndex.scan(List.of(jar, jar)).registrations();
    assertEquals(
        List.of(
            new JarServiceIndex.Registration(
                jar, "javax.annotation.processing.Processor", "sample.Processor"),
            new JarServiceIndex.Registration(
                jar, "sample.RuntimeService", "sample.RuntimeProvider")),
        entries);
  }

  @Test
  void retainsBothOriginsOfASharedProvider() throws Exception {
    var jars = List.of(directory.resolve("first.jar"), directory.resolve("second.jar"));
    for (Path jar : jars) {
      try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
        output.putNextEntry(new JarEntry("META-INF/services/sample.Service"));
        output.write("sample.Provider\n".getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
      }
    }
    assertEquals(
        jars,
        JarServiceIndex.scan(jars).registrations().stream()
            .map(JarServiceIndex.Registration::artifact)
            .toList());
  }
}
