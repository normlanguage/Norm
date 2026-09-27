package dev.w0fv1.norm.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.LINUX)
final class RuntimeImageGeneratorTest {
  @TempDir Path directory;

  @Test
  void createsImageFromAllSystemModulesWithReleaseOptions() throws Exception {
    Path javaHome = fakeJavaHome(false);
    Path image = directory.resolve("dist/runtime");

    RuntimeImageGenerator.generate(javaHome, image);

    assertEquals("java.base,java.net.http,java.xml", Files.readString(image.resolve("modules")));
    String arguments = Files.readString(directory.resolve("jlink-arguments"));
    assertTrue(arguments.contains("--add-modules java.base,java.net.http,java.xml"));
    assertTrue(arguments.contains("--strip-debug"));
    assertTrue(arguments.contains("--no-header-files"));
    assertTrue(arguments.contains("--no-man-pages"));
    assertTrue(arguments.contains("--compress zip-6"));
  }

  @Test
  void failedJlinkPreservesPreviouslyGeneratedImage() throws Exception {
    Path javaHome = fakeJavaHome(true);
    Path image = directory.resolve("dist/runtime");
    Files.createDirectories(image);
    Files.writeString(image.resolve("original"), "retained");

    assertThrows(IOException.class, () -> RuntimeImageGenerator.generate(javaHome, image));

    assertEquals("retained", Files.readString(image.resolve("original")));
  }

  private Path fakeJavaHome(boolean failJlink) throws IOException {
    Path bin = Files.createDirectories(directory.resolve("jdk/bin"));
    Path java = bin.resolve("java");
    Files.writeString(
        java,
        "#!/bin/sh\nprintf 'java.xml@25\\njava.base@25\\njava.net.http@25\\n'\n",
        StandardCharsets.UTF_8);
    java.toFile().setExecutable(true);
    Path jlink = bin.resolve("jlink");
    Files.writeString(
        jlink,
        "#!/bin/sh\n"
            + "printf '%s ' \"$@\" > '"
            + directory.resolve("jlink-arguments")
            + "'\n"
            + (failJlink ? "exit 7\n" : "")
            + "while [ \"$#\" -gt 0 ]; do\n"
            + "  if [ \"$1\" = '--add-modules' ]; then modules=$2; fi\n"
            + "  if [ \"$1\" = '--output' ]; then output=$2; fi\n"
            + "  shift\n"
            + "done\n"
            + "mkdir -p \"$output\"\n"
            + "printf '%s' \"$modules\" > \"$output/modules\"\n",
        StandardCharsets.UTF_8);
    jlink.toFile().setExecutable(true);
    return bin.getParent();
  }
}
