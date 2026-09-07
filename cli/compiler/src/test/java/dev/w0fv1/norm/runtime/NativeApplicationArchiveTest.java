package dev.w0fv1.norm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.testing.NormTestKit;
import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeApplicationArchiveTest {
  @TempDir Path temporaryDirectory;

  @Test
  void roundTripsWithReleaseModuleEncapsulation() throws Exception {
    Path java = Path.of(System.getProperty("java.home"), "bin", "java");
    Path probe =
        Path.of(
            NativeApplicationArchiveProbe.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    Path output = temporaryDirectory.resolve("module-output.txt");
    Process process =
        new ProcessBuilder(
                java.toString(),
                "--sun-misc-unsafe-memory-access=allow",
                "--enable-native-access=org.graalvm.truffle",
                "-Dpolyglot.engine.WarnInterpreterOnly=false",
                "--module-path",
                System.getProperty("norm.test.modulePath"),
                "--patch-module",
                "dev.w0fv1.norm=" + probe,
                "--module",
                "dev.w0fv1.norm/" + NativeApplicationArchiveProbe.class.getName(),
                temporaryDirectory.resolve("module-application.bin").toString())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertTrue(process.waitFor(60, TimeUnit.SECONDS), "modular archive probe timed out");
      String text = Files.readString(output);
      assertEquals(0, process.exitValue(), text);
      assertEquals("native" + System.lineSeparator(), text);
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  @Test
  void roundTripsAPortableCompiledApplication() throws Exception {
    var compilation = NormTestKit.compile("Void main() { printLine(\"native\") }");
    var artifact = compilation.output().orElseThrow().artifact();
    NativeApplicationData expected =
        new NativeApplicationData(
            artifact, CoreExecutionPlan.forArtifact(artifact), List.of(), "sample");
    Path archive = temporaryDirectory.resolve("application.bin");

    NativeApplicationArchive.write(expected, archive);
    NativeApplicationData actual = NativeApplicationArchive.read(archive);

    assertEquals(expected.packageName(), actual.packageName());
    assertEquals(expected.bindings(), actual.bindings());
    assertEquals(expected.artifact().entryPoint(), actual.artifact().entryPoint());
    assertEquals(expected.execution(), actual.execution());
    StringWriter output = new StringWriter();
    new TruffleExecutionBackend()
        .prepare(actual.artifact(), actual.execution())
        .execute(ExecutionContext.of(new PrintWriter(output), JdkSystemPlatform.standard()));
    assertEquals("native" + System.lineSeparator(), output.toString());
  }
}
