package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ApplicationEnvironmentTest {
  @Test
  void exposesIsolatedArgumentsStreamsEnvironmentAndCompletion() {
    var compilation =
        compile(
            """
        import std.application.arguments
        import std.application.environmentVariable
        import std.application.setExitCode
        import std.io.standardInput
        import std.io.readAll
        import std.io.decodeText
        import std.io.TextEncoding
        import std.io.printError
        Void main() {
          printLine(arguments()[0])
          printLine(environmentVariable(name: "NORM_TEST") ?? "missing")
          printLine(decodeText(content: readAll(reader: standardInput(), maximumBytes: 100), encoding: TextEncoding.Utf8))
          printError(text: "diagnostic")
          setExitCode(code: 2)
        }
        """);
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    var output = new StringWriter();
    var error = new StringWriter();
    var context =
        ExecutionContext.builder()
            .arguments(List.of("a  b"))
            .environment(Map.of("NORM_TEST", "isolated"))
            .input(new ByteArrayInputStream("中文\ninput".getBytes(StandardCharsets.UTF_8)))
            .output(new PrintWriter(output))
            .error(new PrintWriter(error))
            .platform(JdkSystemPlatform.standard())
            .build();
    new NormRuntime().run(compilation.output().orElseThrow().artifact(), context);
    assertEquals("a  b\nisolated\n中文\ninput\n", output.toString().replace("\r\n", "\n"));
    assertEquals("diagnostic", error.toString());
    assertEquals(2, context.exitCode());
    assertEquals(0, ExecutionContext.builder().build().exitCode());
  }
}
