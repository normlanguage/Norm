package dev.w0fv1.norm.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

final class RuntimeLauncherGeneratorTest {
  @TempDir Path directory;

  @Test
  void bundledLauncherUsesItsPrivateRuntimeAndPreservesDescriptor() throws Exception {
    RuntimeLauncherGenerator.generate(
        directory.resolve("bundled"),
        RuntimeLauncherGenerator.JavaRuntime.BUNDLED,
        "dev.w0fv1.norm",
        "dev.w0fv1.norm.cli.Main",
        List.of("--add-modules=java.se", "--enable-native-access=org.graalvm.truffle"));

    Path bin = directory.resolve("bundled");
    String shell = Files.readString(bin.resolve("norm"));
    String batch = Files.readString(bin.resolve("norm.bat"));
    assertTrue(Files.isExecutable(bin.resolve("norm")));
    assertTrue(shell.contains("exec \"$APP_HOME/runtime/bin/java\""));
    assertTrue(batch.contains("%APP_HOME%\\runtime\\bin\\java.exe"));
    assertTrue(shell.contains("--module dev.w0fv1.norm/dev.w0fv1.norm.cli.Main \"$@\""));
    var descriptor =
        JsonParser.parseString(Files.readString(bin.resolve("launcher.json"))).getAsJsonObject();
    assertEquals("dev.w0fv1.norm/dev.w0fv1.norm.cli.Main", descriptor.get("module").getAsString());
    assertEquals(2, descriptor.getAsJsonArray("jvmArguments").size());
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void systemLauncherRunsWithJavaFromPath() throws Exception {
    Path bin = directory.resolve("system/bin");
    RuntimeLauncherGenerator.generate(
        bin,
        RuntimeLauncherGenerator.JavaRuntime.SYSTEM,
        "dev.w0fv1.norm",
        "dev.w0fv1.norm.cli.Main",
        List.of());

    Path fakeJava = directory.resolve("fake-java/java");
    Files.createDirectories(fakeJava.getParent());
    Files.writeString(fakeJava, "#!/bin/sh\nprintf '%s\\n' \"$@\"\n", StandardCharsets.UTF_8);
    fakeJava.toFile().setExecutable(true);
    var started = new ProcessBuilder(bin.resolve("norm").toString(), "--version");
    started.environment().put("PATH", fakeJava.getParent() + ":" + System.getenv("PATH"));
    started.environment().remove("JAVA_HOME");
    var result = started.start();
    String output = new String(result.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, result.waitFor());
    assertTrue(output.contains("--module-path"));
    assertTrue(output.contains(directory.resolve("system/lib").toString()));
    assertTrue(output.contains("--module"));
    assertTrue(output.contains("dev.w0fv1.norm/dev.w0fv1.norm.cli.Main"));
    assertTrue(output.contains("--version"));
  }
}
