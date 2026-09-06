package dev.w0fv1.norm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.testing.NormTestKit;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeApplicationArchiveTest {
  @TempDir Path temporaryDirectory;

  @Test
  void roundTripsAPortableCompiledApplication() throws Exception {
    var compilation = NormTestKit.compile("Void main() { printLine(\"native\") }");
    var artifact = compilation.program().orElseThrow().compilation().artifact();
    NativeApplicationData expected =
        new NativeApplicationData(
            artifact,
            dev.w0fv1.norm.core.CoreExecutionPlan.forArtifact(artifact),
            List.of(),
            "sample");
    Path archive = temporaryDirectory.resolve("application.bin");

    NativeApplicationArchive.write(expected, archive);
    NativeApplicationData actual = NativeApplicationArchive.read(archive);

    assertEquals(expected.packageName(), actual.packageName());
    assertEquals(expected.bindings(), actual.bindings());
    assertEquals(expected.artifact().entryPoint(), actual.artifact().entryPoint());
    assertEquals(expected.execution(), actual.execution());
    StringWriter output = new StringWriter();
    new dev.w0fv1.norm.truffle.TruffleExecutionBackend()
        .prepare(actual.artifact(), actual.execution())
        .execute(ExecutionContext.of(new PrintWriter(output), JdkSystemPlatform.standard()));
    assertEquals("native" + System.lineSeparator(), output.toString());
  }
}
